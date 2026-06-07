# poke-engine-bridge container — foul-play + poke-engine + the FastAPI bridge.
#
# The engine is compiled WITHOUT the terastallization cargo feature (the server
# runs tera-free; this removes tera from the search itself rather than just
# filtering "-tera" picks at selection time, which stays as a second layer).
#
# Build (Apple Silicon host -> amd64 cluster) and push to the LAN registry:
#
#   docker buildx build --platform linux/amd64 \
#     -t registry.ecliptic.internal/poke-engine-bridge:latest \
#     --push ops/poke-engine-bridge
#
# Keep FOUL_PLAY_COMMIT in sync with .github/workflows/deploy-bridge.yml.

FROM python:3.12-slim AS builder

ARG FOUL_PLAY_COMMIT=13db2786b4fdbc742d627e21dd4ee1de6bf963b0

RUN apt-get update \
    && apt-get install -y --no-install-recommends git curl build-essential \
    && rm -rf /var/lib/apt/lists/*

# Rust toolchain — poke-engine builds from sdist via maturin.
RUN curl -sSf https://sh.rustup.rs | sh -s -- -y --profile minimal --default-toolchain stable
ENV PATH="/root/.cargo/bin:${PATH}"

RUN git clone https://github.com/pmariglia/foul-play.git /foul-play \
    && cd /foul-play \
    && git fetch --depth 1 origin "${FOUL_PLAY_COMMIT}" \
    && git checkout "${FOUL_PLAY_COMMIT}" \
    && rm -rf .git

RUN python -m venv /venv

# foul-play pins poke-engine with build-args enabling terastallization;
# strip that feature flag so the engine compiles without tera.
RUN sed 's| --features poke-engine/terastallization||' /foul-play/requirements.txt > /tmp/requirements.txt \
    && /venv/bin/pip install --no-cache-dir -r /tmp/requirements.txt

# Override stock poke-engine 0.0.46 with our patched build (hspahic-cs/poke-engine
# fork), which adds the opponent-fallibility `temperature` param to the MCTS so
# the gym AI can punish greedy play. Built gen9 + tera-free: the fork is at the
# poke-engine `main` HEAD where a generation feature is mandatory (root crate
# `default = []`; poke-engine-py default is gen4), so we select gen9 explicitly
# and omit `terastallization` to keep the search tera-free. Bump POKE_ENGINE_REF
# to track the fork.
ARG POKE_ENGINE_REF=38fca549475c0fac3f9941bd0ddcf0bc228b19a0
RUN /venv/bin/pip install --no-cache-dir --force-reinstall --no-deps \
    "poke-engine @ git+https://github.com/hspahic-cs/poke-engine.git@${POKE_ENGINE_REF}#subdirectory=poke-engine-py" \
    --config-settings="build-args=--features poke-engine/gen9 --no-default-features"

COPY requirements.txt /tmp/bridge-requirements.txt
RUN /venv/bin/pip install --no-cache-dir -r /tmp/bridge-requirements.txt


FROM python:3.12-slim

COPY --from=builder /venv /venv
COPY --from=builder /foul-play /foul-play
COPY app.py bridge.py replay.py /app/

# foul-play writes Smogon set caches under its own data/ dir at runtime.
RUN useradd -u 1000 -m bridge \
    && chown -R bridge:bridge /foul-play /app \
    && mkdir -p /battle-logs && chown bridge:bridge /battle-logs

USER bridge
WORKDIR /app
ENV PYTHONPATH=/foul-play \
    PORT=8642 \
    WORKERS=3 \
    BRIDGE_MCTS_BATCHES=3 \
    BRIDGE_BATTLE_LOG_DIR=/battle-logs

EXPOSE 8642

CMD ["/bin/sh", "-c", "/venv/bin/uvicorn app:app --host 0.0.0.0 --port ${PORT} --workers ${WORKERS} --log-level info"]

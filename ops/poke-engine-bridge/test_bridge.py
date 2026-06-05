"""Tests for the perfect-information path in bridge.py.

Run from this directory with foul-play on PYTHONPATH:

    PYTHONPATH=../../reference/foul-play \
        ../../reference/foul-play/.venv/bin/python -m pytest test_bridge.py -q
"""

import constants
from fp.battle import Battle, Pokemon

from bridge import (
    PackedPokemon,
    PickRequest,
    overlay_opponent_team,
    parse_packed_team,
    safest_move,
    select_choice,
    strip_cobblemon_uuids,
)


def packed_entry(
    name="garchomp",
    hp=170,
    status="",
    item="choicescarf",
    ability="roughskin",
    moves="earthquake,outrage,stoneedge,swordsdance",
    pp="10/10,10/10,8/8,16/16",
    nature="jolly",
    evs="0,252,0,0,4,252",
    gender="M",
    ivs="31,31,31,31,31,31",
    shiny="",
    level=78,
    misc="255,pokeball,,,,DRAGON",
):
    # Cobblemon packTeam() layout — see bridge.py for field map
    uuid = "00000000-0000-0000-0000-000000000000"
    return (
        f"{name}||{uuid}|{hp}|{status}|-1|{item}|{ability}|{moves}|{pp}"
        f"|{nature}|{evs}|{gender}|{ivs}|{shiny}|{level}|{misc}"
    )


def test_parse_packed_team_two_mons():
    packed = "]".join(
        [
            packed_entry(),
            packed_entry(
                name="pikachu",
                hp=0,
                status="psn",
                item="",
                ability="static",
                moves="thunderbolt,surf",
                pp="15/15,15/15",
                nature="timid",
                level=50,
                misc="255,pokeball,,,,ELECTRIC",
            ),
        ]
    )
    mons = parse_packed_team(packed)
    assert len(mons) == 2

    chomp = mons[0]
    assert chomp.name == "garchomp"
    assert chomp.hp == 170
    assert chomp.status is None
    assert chomp.item == "choicescarf"
    assert chomp.ability == "roughskin"
    assert chomp.moves == ["earthquake", "outrage", "stoneedge", "swordsdance"]
    assert chomp.nature == "jolly"
    assert chomp.evs == (0, 252, 0, 0, 4, 252)
    assert chomp.ivs == (31,) * 6
    assert chomp.level == 78
    assert chomp.tera_type == "dragon"

    pika = mons[1]
    assert pika.name == "pikachu"
    assert pika.hp == 0
    assert pika.status == "psn"
    assert pika.item is None
    assert pika.moves == ["thunderbolt", "surf"]


def _battle_with_revealed_opponent() -> Battle:
    battle = Battle(battle_tag="test-battle")
    battle.opponent.active = Pokemon.from_switch_string("Garchomp, L78, M")
    return battle


def test_overlay_fills_unrevealed_truth():
    battle = _battle_with_revealed_opponent()
    active = battle.opponent.active
    active.add_move("earthquake")  # revealed via log
    assert active.item == constants.UNKNOWN_ITEM
    assert active.ability is None

    overlay_opponent_team(battle, parse_packed_team(packed_entry()))

    assert {m.name for m in active.moves} == {
        "earthquake",
        "outrage",
        "stoneedge",
        "swordsdance",
    }
    assert active.item == "choicescarf"
    assert active.ability == "roughskin"
    assert active.nature == "jolly"
    assert active.evs == (0, 252, 0, 0, 4, 252)


def test_overlay_does_not_resurrect_removed_item():
    battle = _battle_with_revealed_opponent()
    # log replay tracked a knock off: item is None, not UNKNOWN
    battle.opponent.active.item = None

    overlay_opponent_team(battle, parse_packed_team(packed_entry()))

    assert battle.opponent.active.item is None


def test_overlay_preserves_hp_fraction_across_spread_change():
    battle = _battle_with_revealed_opponent()
    active = battle.opponent.active
    active.hp = active.max_hp // 2
    fraction_before = active.hp / active.max_hp

    overlay_opponent_team(battle, parse_packed_team(packed_entry()))

    assert abs(active.hp / active.max_hp - fraction_before) < 0.01
    # real spread applied: jolly 252 Spe at L78 outruns the default-spread calc
    assert active.stats[constants.SPEED] > 150


def test_select_choice_clear_winner():
    # one dominant option: near-best set is a singleton, always picked
    options = [("psychic", 900, 0.6), ("switch bronzong", 100, 0.55)]
    assert select_choice(options) == "psychic"


def test_select_choice_anti_defeatism_prefers_attack_when_losing():
    # losing position (score < 0.35), top pick is a switch, attack has
    # >= 40% of its visits -> attack overrides
    options = [
        ("switch slowbro", 500, 0.15),
        ("icepunch", 250, 0.14),
        ("switch reuniclus", 200, 0.13),
    ]
    assert select_choice(options) == "icepunch"


def test_select_choice_no_override_when_winning():
    # same shape but healthy eval: switch stands (attack is below the 75%
    # near-best cutoff, so the draw can't pick it either)
    options = [("switch slowbro", 500, 0.60), ("icepunch", 250, 0.59)]
    assert select_choice(options) == "switch slowbro"


def test_select_choice_no_override_without_credible_attack():
    # losing, but the only attack has a tiny visit share: keep the switch
    options = [("switch slowbro", 500, 0.10), ("tackle", 50, 0.02)]
    assert select_choice(options) == "switch slowbro"


def test_select_choice_forced_switch_unaffected():
    # force-switch turns offer only switches; override is a no-op
    options = [("switch slowbro", 300, 0.10), ("switch reuniclus", 280, 0.09)]
    assert select_choice(options) in ("switch slowbro", "switch reuniclus")


def test_safest_move_maximin():
    # row worst-cases: psychic -> 1.0, switch -> 2.0 => maximin picks switch
    s1 = ["psychic", "switch bronzong"]
    s2 = ["nightslash", "swordsdance"]
    matrix = [5.0, 1.0, 2.0, 3.0]
    assert safest_move(s1, s2, matrix) == "switch bronzong"


def test_safest_move_ignores_pruned_cells():
    # pruned (None) cells aren't a realized worst case; psychic's only real
    # cell is 4.0 which beats switch's worst of 3.0
    s1 = ["psychic", "switch bronzong"]
    s2 = ["nightslash", "swordsdance"]
    matrix = [4.0, None, 3.0, 3.5]
    assert safest_move(s1, s2, matrix) == "psychic"


def test_safest_move_fully_pruned_row_never_wins():
    s1 = ["psychic", "switch bronzong"]
    s2 = ["nightslash"]
    matrix = [None, -1.0]
    assert safest_move(s1, s2, matrix) == "switch bronzong"


def _uuid_pick_request():
    # exact shapes from the 2026-06-05 live audit dump
    slowbro_uuid = "28c0d5db-9b76-4780-97ce-48315f40bc09"
    torterra_uuid = "e32a67ca-92d1-43bf-a21d-f30d9cf4bbd2"
    request_json = {
        "side": {
            "id": "p2",
            "pokemon": [
                {
                    "ident": "p2: Slowbro",
                    "details": f"Slowbro, {slowbro_uuid}, L50, M",
                    "condition": "202/202",
                    "active": True,
                    "stats": {"atk": 85, "def": 130, "spa": 130, "spd": 90, "spe": 50},
                    "moves": ["futuresight", "teleport", "scald", "slackoff"],
                    "ability": "regenerator",
                    "item": "heavydutyboots",
                }
            ],
        }
    }
    log_lines = [
        f"|switch|p1a: {torterra_uuid}|Torterra, {torterra_uuid}, L55, M|100/100",
        f"|switch|p2a: {slowbro_uuid}|Slowbro, {slowbro_uuid}, L50, M|202/202",
        f"|move|p1a: {torterra_uuid}|Earthquake|p2a: {slowbro_uuid}",
    ]
    return PickRequest(
        request_json=request_json,
        log_lines=log_lines,
        gym_side="p2",
        pokemon_format="gen9customgame",
        generation="gen9",
        smogon_stats_format="gen9nationaldex",
        search_time_ms=1000,
    )


def test_strip_cobblemon_uuids_details_and_idents():
    req = strip_cobblemon_uuids(_uuid_pick_request())

    pkmn = req.request_json["side"]["pokemon"][0]
    # details parseable by from_switch_string again: name, L50, M
    assert pkmn["details"] == "Slowbro, L50, M"
    # untouched fields survive the JSON round-trip
    assert pkmn["moves"] == ["futuresight", "teleport", "scald", "slackoff"]
    assert pkmn["stats"]["def"] == 130

    assert req.log_lines == [
        "|switch|p1a: Torterra|Torterra, L55, M|100/100",
        "|switch|p2a: Slowbro|Slowbro, L50, M|202/202",
        "|move|p1a: Torterra|Earthquake|p2a: Slowbro",
    ]


def test_strip_cobblemon_uuids_level_parses():
    req = strip_cobblemon_uuids(_uuid_pick_request())
    pkmn = Pokemon.from_switch_string(req.request_json["side"]["pokemon"][0]["details"])
    assert pkmn.level == 50  # was 100: the UUID token broke level parsing


def test_request_active_mon_survives_replay():
    """The lead mon must not become a phantom when it is also the current active.

    Stateless rebuild: the latest request marks Slowbro active NOW, while the
    replay starts at turn 0 with Slowbro's lead |switch|. switch_or_drag only
    searches the reserve, so without the demotion in _build_battle the replay
    fabricates a moveless phantom (observed live: gym lead had no moves
    whenever it was on the field, 'No Move' in a 1v1 endgame).
    """
    from fp.battle_modifier import process_battle_updates

    battle = Battle(battle_tag="phantom-test")
    battle.generation = "gen9"
    battle.user.name = "p2"
    battle.opponent.name = "p1"
    battle.user.initialize_first_turn_user_from_json(
        {
            "side": {
                "id": "p2",
                "pokemon": [
                    {
                        "ident": "p2: Slowbro",
                        "details": "Slowbro, L50, M",
                        "condition": "202/202",
                        "active": True,
                        "stats": {"atk": 85, "def": 130, "spa": 130, "spd": 90, "spe": 50},
                        "moves": ["futuresight", "teleport", "scald", "slackoff"],
                        "ability": "regenerator",
                        "item": "heavydutyboots",
                    },
                    {
                        "ident": "p2: Bronzong",
                        "details": "Bronzong, L50",
                        "condition": "174/174",
                        "active": False,
                        "stats": {"atk": 109, "def": 136, "spa": 99, "spd": 136, "spe": 43},
                        "moves": ["stealthrock", "trickroom", "gyroball", "toxic"],
                        "ability": "levitate",
                        "item": "leftovers",
                    },
                ],
            }
        }
    )
    battle.started = True
    battle.msg_list = [
        "|switch|p1a: Greninja|Greninja, L53, M|100/100",
        "|switch|p2a: Slowbro|Slowbro, L50, M|202/202",
        "|switch|p2a: Bronzong|Bronzong, L50|174/174",
        "|switch|p2a: Slowbro|Slowbro, L50, M|202/202",
    ]

    # the demotion under test (mirrors _build_battle)
    battle.user.reserve.append(battle.user.active)
    battle.user.active = None
    process_battle_updates(battle)

    active = battle.user.active
    assert active.name == "slowbro"
    assert {m.name for m in active.moves} == {
        "futuresight",
        "teleport",
        "scald",
        "slackoff",
    }
    assert active.ability == "regenerator"
    assert active.item == "heavydutyboots"
    # no phantom duplicate left behind
    assert [p.name for p in battle.user.reserve] == ["bronzong"]


def test_overlay_adds_unrevealed_mon_to_reserve():
    battle = _battle_with_revealed_opponent()
    packed = "]".join(
        [
            packed_entry(),
            packed_entry(
                name="pikachu",
                hp=10,
                status="brn",
                item="lightball",
                ability="static",
                moves="thunderbolt,surf",
                pp="15/15,15/15",
                nature="timid",
                evs="0,0,0,0,0,0",
                level=50,
                misc="255,pokeball,,,,ELECTRIC",
            ),
        ]
    )

    overlay_opponent_team(battle, parse_packed_team(packed))

    assert len(battle.opponent.reserve) == 1
    pika = battle.opponent.reserve[0]
    assert pika.name == "pikachu"
    assert pika.level == 50
    assert pika.hp == 10  # raw current HP from the sheet
    assert pika.status == "brn"
    assert pika.item == "lightball"
    assert {m.name for m in pika.moves} == {"thunderbolt", "surf"}

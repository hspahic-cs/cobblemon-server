"""Tests for the perfect-information path in bridge.py.

Run from this directory with foul-play on PYTHONPATH:

    PYTHONPATH=../../reference/foul-play \
        ../../reference/foul-play/.venv/bin/python -m pytest test_bridge.py -q
"""

import constants
from fp.battle import Battle, Pokemon

from bridge import PackedPokemon, overlay_opponent_team, parse_packed_team


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

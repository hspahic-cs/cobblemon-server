package com.cobblemonserver.npc.battle

import com.cobblemonserver.npc.data.NpcTeamData
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

object BattleDialogue {

    private val standardOpeners = listOf(
        "Let's see what you've got!",
        "I've been waiting for a real challenge.",
        "Don't hold back — I won't.",
        "Hope you're ready. I've been training.",
        "A battle? You're on!",
        "Oh, finally — someone worth my time.",
        "Been practicing all morning. Let's go.",
        "I'll keep this brief: I intend to win.",
        "Pokemon out! No warm-ups.",
        "You picked the wrong citizen to challenge.",
        "Careful now — I don't lose often.",
        "Show me what the wilds taught you.",
        "My team's fed and rested. Yours?",
        "I'll go easy… no, I won't. Begin.",
        "Just so we're clear — I'm playing to win."
    )

    private val gymLeaderOpeners = listOf(
        "So you want the badge? Earn it.",
        "Step into my gym. Show me your resolve.",
        "Many challengers have stood where you do. Few have left victorious.",
        "I am this gym's leader. Prepare yourself.",
        "Let this be a battle worth remembering.",
        "Badges are not given. They are taken.",
        "My team has waited patiently for a worthy opponent.",
        "You come to my gym — then fight like it matters.",
        "Let us see if your training was enough.",
        "I have guarded this floor for a long time. Prove you belong here.",
        "Battle me, and you will understand why I hold this post.",
        "The floor is yours, challenger. Make it count.",
        "You've climbed the tower. Now face its keeper.",
        "No quarter, no excuses. Begin.",
        "Show me the trainer worth the badge."
    )

    private val standardWins = listOf(
        "Better luck next time!",
        "Not bad, but not enough.",
        "Training pays off, doesn't it?",
        "Ha! Come back stronger.",
        "You fought well. Try again soon.",
        "So close! Keep at it.",
        "That's how it's done around here.",
        "Don't take it personally — I've been at this a while.",
        "You've got heart. Just need more training.",
        "Close match. I mean that.",
        "Walk it off. You'll get me next time.",
        "Maybe study up on type matchups, hm?",
        "No shame in losing to me.",
        "A win's a win. Good battle.",
        "Come find me when you're ready for a rematch."
    )

    private val gymLeaderWins = listOf(
        "Not yet. Come back when you're stronger.",
        "A gym leader's duty is to test — and you have been tested.",
        "You fought bravely. The badge remains mine.",
        "Return when your team has grown.",
        "Defeat is a teacher. Learn from this.",
        "Close, but my gym stands firm.",
        "You have promise. Sharpen it and return.",
        "A good showing — but not good enough.",
        "The badge is not ready to leave my hand.",
        "Train. Reflect. Return. That is the cycle.",
        "You'll be back. I can see it in your eyes.",
        "A worthy attempt. Now go and improve.",
        "This gym does not yield to the unready.",
        "Your team fought well. It simply was not their day.",
        "Do not be discouraged. Most never challenge at all."
    )

    private val standardLosses = listOf(
        "A fine match! I yield.",
        "You're stronger than I thought.",
        "Well fought! I needed that.",
        "I'll train harder. Count on a rematch.",
        "You have real talent.",
        "Oof. That was a proper thrashing.",
        "Okay, okay — you win this round.",
        "Respect. Your team is no joke.",
        "I needed that loss, honestly.",
        "Back to the drawing board for me.",
        "You fight like a champion in the making.",
        "Well played, truly. I'll remember this.",
        "You knew exactly what to do.",
        "I'll be ready next time. Probably.",
        "Good battle. I mean it — good battle."
    )

    private val gymLeaderLosses = listOf(
        "You have proven yourself. Well earned.",
        "A gym leader knows when they are beaten. Take your victory.",
        "Splendid! You've earned your place.",
        "I concede — your team is magnificent.",
        "You have my respect, challenger.",
        "Few fight like that. Fewer still deserve to win.",
        "The badge is yours by right. Wear it with honor.",
        "A flawless performance. My team salutes you.",
        "I trained for years. You were ready regardless.",
        "You fought like the gym was already yours.",
        "This loss will be talked about for seasons.",
        "Go now, champion-in-training. The path continues.",
        "I stand defeated — and proud to have faced you.",
        "You've earned more than a badge today.",
        "A new era begins. Carry it well."
    )

    private val standardFirstDefeat = listOf(
        "You... you actually beat me? I thought I had this one!",
        "First blood. Enjoy it while it lasts.",
        "I'll remember this. Consider yourself on my list.",
        "Nobody's beaten me before. How does that FEEL?",
        "Huh. I genuinely did not see that coming.",
        "Beginner's luck. Say it was beginner's luck.",
        "You caught me off guard. Won't happen twice.",
        "Congratulations on your first win. Savor it.",
        "Well. That's new. I suppose I deserved that.",
        "A first defeat cuts the deepest. Well played."
    )

    private val gymLeaderFirstDefeat = listOf(
        "Few have done what you just did. Wear that pride well.",
        "Your first victory here will not be forgotten — by you or by me.",
        "Impressive. The badge is yours by right.",
        "This is the moment you'll look back on. Burn it in.",
        "I stand defeated for the first time in a long while. Well earned.",
        "You have breached my gym. History begins here.",
        "A first win at my floor is no small thing. Honor it.",
        "I concede. And I will train again — because of you.",
        "Mark this day. I certainly will.",
        "You have crossed the threshold. The tower opens further."
    )

    fun onBattleStart(citizenName: String, data: NpcTeamData?, player: ServerPlayer) {
        send(citizenName, player, pickLine(data, gymLeaderOpeners, standardOpeners))
    }

    fun onNpcWin(citizenName: String, data: NpcTeamData?, player: ServerPlayer) {
        send(citizenName, player, pickLine(data, gymLeaderWins, standardWins))
    }

    fun onNpcLose(citizenName: String, data: NpcTeamData?, player: ServerPlayer, wasFirstDefeat: Boolean) {
        val gym = if (wasFirstDefeat) gymLeaderFirstDefeat else gymLeaderLosses
        val normal = if (wasFirstDefeat) standardFirstDefeat else standardLosses
        send(citizenName, player, pickLine(data, gym, normal))
    }

    private fun pickLine(data: NpcTeamData?, gym: List<String>, normal: List<String>): String {
        val isGymLeader = data?.gymLeaderTheme != null
        return (if (isGymLeader) gym else normal).random()
    }

    private fun send(citizenName: String, player: ServerPlayer, line: String) {
        val name = Component.literal("<$citizenName>").withStyle(ChatFormatting.AQUA)
        val body = Component.literal(" $line").withStyle(ChatFormatting.WHITE)
        player.sendSystemMessage(name.append(body))
    }
}

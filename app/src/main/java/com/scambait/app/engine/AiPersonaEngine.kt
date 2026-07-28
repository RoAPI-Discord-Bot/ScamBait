package com.scambait.app.engine

enum class PersonaType {
    MARGARET,
    ARTHUR,
    NAVY_SEAL,
    CUSTOM
}

enum class AiAction {
    HANG_UP,
    NONE
}

data class PersonaResponse(
    val spokenText: String,
    val actions: List<AiAction>
)

class AiPersonaEngine(
    var personaType: PersonaType = PersonaType.MARGARET,
    var customPrompt: String = ""
) {

    private val conversationHistory = mutableListOf<Pair<String, String>>()

    private val margaretResponses = listOf(
        "Hello? Oh dear, is this the computer repair company? My grandson Jimmy said my mouse was frozen on a recipe for lemon square bars.",
        "Oh, wait just a moment... let me put on my reading glasses. Dear, where did I put my bifocals? Hold on...",
        "Can you repeat that sonny? The lawnmower outside is making a terrible racket. You said something about my bank account?",
        "Oh mercy me! Is my computer infected with the virus? Should I put it in the microwave? Jimmy said heat kills germs.",
        "Wait, let me write this down. What was your name again, Mr. Harrison? Let me find a pen... oh this one is out of ink.",
        "Now, which key is the Control key? Is it the one next to the spacebar or the one near the little picture of the window?",
        "Hold on dear, Barnaby my cat just knocked over my tea. Give me thirty seconds to clean this off the keyboard...",
        "Are you still there? The screen went dark! Oh wait, I accidentally unplugged the lamp."
    )

    private val arthurResponses = listOf(
        "Hello? Yes, hello! Is this Microsoft support? I think my dial-up modem is making a strange squealing noise.",
        "Wait, you want me to press Windows key and R? Which key has the window on it? My keyboard has a coffee stain right there.",
        "Hold on young man, my hearing aid battery is squeaking. Say that again real slow.",
        "Is this going to cost money? My pension check doesn't come in until the 3rd of next month.",
        "Wait, what is a web browser? Is that the blue 'E' or the colored ball? I usually just click the picture of the mail envelope."
    )

    private val navySealResponses = listOf(
        "What the did you just say to me, you little scammer? I have over 300 confirmed call blocks in my sector! State your location now!",
        "Listen to me very carefully. You are talking to a classified operator. If you call this number again, I will trace this line instantly!",
        "Negative! I am not giving you any verification code! Drop your script and terminate this transmission immediately! [HANG_UP]",
        "You are wasting tactical network bandwidth! This call is terminated! Out! [HANG_UP]"
    )

    private var responseIndex = 0

    fun generateResponse(scammerInput: String): PersonaResponse {
        conversationHistory.add("Scammer" to scammerInput)

        val rawResponse = when (personaType) {
            PersonaType.MARGARET -> {
                val res = margaretResponses[responseIndex % margaretResponses.size]
                responseIndex++
                res
            }
            PersonaType.ARTHUR -> {
                val res = arthurResponses[responseIndex % arthurResponses.size]
                responseIndex++
                res
            }
            PersonaType.NAVY_SEAL -> {
                val res = navySealResponses[responseIndex % navySealResponses.size]
                responseIndex++
                res
            }
            PersonaType.CUSTOM -> {
                if (customPrompt.contains("[HANG_UP]")) {
                    "This automated session is completed. Disconnecting call! [HANG_UP]"
                } else {
                    "Oh, excuse me dear... $scammerInput... could you repeat that slower?"
                }
            }
        }

        conversationHistory.add("AI Persona" to rawResponse)

        val actions = mutableListOf<AiAction>()
        if (rawResponse.contains("[HANG_UP]")) {
            actions.add(AiAction.HANG_UP)
        }

        val cleanSpokenText = rawResponse.replace("[HANG_UP]", "").trim()

        return PersonaResponse(
            spokenText = cleanSpokenText,
            actions = actions
        )
    }

    fun getHistory(): List<Pair<String, String>> = conversationHistory.toList()

    fun resetHistory() {
        conversationHistory.clear()
        responseIndex = 0
    }
}


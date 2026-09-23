package in.codebuffdev.boot.routing.tier;

public enum GeminiModelTier {

    FLASH_LITE(
            "gemini-3.5-flash-lite",
            "Lightweight, latency-sensitive requests where speed and cost matter most."),

    FLASH_3_6(
            "gemini-3.6-flash",
            "High-efficiency workhorse: everyday coding, web tasks, and fast agentic tool use."),

    FLASH_3_8(
            "gemini-3.8-flash",
            "High-intensity Flash reasoning: long-horizon coding, agentic planning, and iterative tool calling.");


    private final String modelId;
    private final String description;

    GeminiModelTier(String modelId, String description) {
        this.modelId = modelId;
        this.description = description;
    }

    public String modelId() {
        return this.modelId;
    }

    public String description() {
        return this.description;
    }
}
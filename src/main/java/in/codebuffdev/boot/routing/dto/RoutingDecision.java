package in.codebuffdev.boot.routing.dto;

import in.codebuffdev.boot.routing.tier.GeminiModelTier;

import java.util.Map;

public record RoutingDecision(GeminiModelTier tier, String model, double confidence, Map<String, Double> probabilities){
}
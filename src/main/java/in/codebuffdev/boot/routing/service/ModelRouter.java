package in.codebuffdev.boot.routing.service;

import in.codebuffdev.boot.routing.tier.GeminiModelTier;
import in.codebuffdev.boot.routing.dto.RoutingDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springaicommunity.typesafe.question.Choice;
import org.springaicommunity.typesafe.response.ChoiceAnswer;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ModelRouter {

    private static final Logger LOG = LoggerFactory.getLogger(ModelRouter.class);

    public final String QUESTION = "tier";

    private final TypeSafeClient  typeSafeClient;
    private final Choice tierChoice;

    public ModelRouter(TypeSafeClient typeSafeClient) {
        this.typeSafeClient = typeSafeClient;

        Choice.Builder choiceInstructions = Choice.builder().instructions("Which model tier is the cheapest one that can still answer this prompt well? Prefer the cheaper tier unless the prompt clearly needs more capability.");

        for(GeminiModelTier tier : GeminiModelTier.values()) {
            choiceInstructions.option(tier.name(), tier.description());
        }

        tierChoice = choiceInstructions.build();
    }

    public RoutingDecision route(String prompt){
        ChoiceAnswer choiceAnswer = typeSafeClient.systemOne(prompt, Map.of(QUESTION, this.tierChoice)).choice(QUESTION);
        GeminiModelTier tier = GeminiModelTier.valueOf(choiceAnswer.value());
        LOG.info("Routing to {} with confidence {}", tier.modelId(), choiceAnswer.confidence());
        return new RoutingDecision(tier, tier.modelId(), choiceAnswer.confidence(), choiceAnswer.probabilities());
    }

}

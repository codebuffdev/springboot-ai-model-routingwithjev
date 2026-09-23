package in.codebuffdev.boot.routing.controller;

import in.codebuffdev.boot.routing.dto.RoutingDecision;
import in.codebuffdev.boot.routing.service.ModelRouter;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatController {

    private final ModelRouter  modelRouter;

    private final ChatClient  chatClient;

    public ChatController(ChatClient chatClient, ModelRouter modelRouter) {
        this.chatClient = chatClient;
        this.modelRouter = modelRouter;
    }

    @PostMapping("/chat")
    public ChatResult chat(@RequestBody ChatRequest request){
        RoutingDecision routingDecision = modelRouter.route(request.prompt());

        String content = chatClient.prompt()
                .user(request.prompt())
                .options(ChatOptions.builder().model(routingDecision.model()))
                .call()
                .content();

        return new ChatResult(routingDecision, content);
    }

    public record ChatRequest(String prompt) {}
    public record ChatResult(RoutingDecision routingDecision, String answer){}

}

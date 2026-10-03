package com.payflow.notify;

import com.payflow.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/disputes")
public class DisputeController {

    private final CurrentUser currentUser;
    private final DisputeService disputes;

    public DisputeController(CurrentUser currentUser, DisputeService disputes) {
        this.currentUser = currentUser;
        this.disputes = disputes;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DisputeService.DisputeView open(@Valid @RequestBody OpenRequest request) {
        return disputes.open(currentUser.require().id(), request.transactionId(), request.note());
    }

    @GetMapping
    public List<DisputeService.DisputeView> mine() {
        return disputes.mine(currentUser.require().id());
    }

    public record OpenRequest(@NotNull UUID transactionId, @NotNull @Size(min = 3, max = 500) String note) {
    }
}

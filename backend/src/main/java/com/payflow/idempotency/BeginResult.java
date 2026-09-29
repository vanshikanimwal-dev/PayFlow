package com.payflow.idempotency;

public sealed interface BeginResult<T> permits BeginResult.Proceed, BeginResult.Replay, BeginResult.Recover {

    record Proceed<T>() implements BeginResult<T> {
    }

    record Replay<T>(T body) implements BeginResult<T> {
    }

    record Recover<T>() implements BeginResult<T> {
    }
}

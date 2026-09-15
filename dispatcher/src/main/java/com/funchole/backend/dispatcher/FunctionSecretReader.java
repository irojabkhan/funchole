package com.funchole.backend.dispatcher;

public interface FunctionSecretReader {

    String read(String secretRef);
}

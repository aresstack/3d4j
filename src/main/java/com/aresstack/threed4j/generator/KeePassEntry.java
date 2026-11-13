package com.aresstack.threed4j.generator;

/**
 * A KeePass entry as far as the generator needs it.
 *
 * @param url      the entry's first http(s) address, or an empty string; for an AI
 *                 service this is the base address of its API
 * @param password for an AI service: the API key
 */
public record KeePassEntry(String title, String url, String password) {

    public boolean hasUrl() {
        return !url.isEmpty();
    }
}

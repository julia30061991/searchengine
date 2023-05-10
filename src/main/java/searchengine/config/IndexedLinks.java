package searchengine.config;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
public class IndexedLinks {
    private Set<String> indexedLinks = new CopyOnWriteArraySet<>();

    public Set<String> getIndexedLinks() {
        return indexedLinks;
    }
}
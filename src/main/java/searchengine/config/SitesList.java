package searchengine.config;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Getter
@Setter
@Component
@Data
@ConfigurationProperties(prefix = "indexing-settings")
public class SitesList {
    private List<SiteConfig> sites;

    public List<SiteConfig> getSites() {
        return sites;
    }
}
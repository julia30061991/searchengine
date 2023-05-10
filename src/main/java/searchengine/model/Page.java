package searchengine.model;

import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import javax.persistence.*;
import javax.persistence.Index;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

@NoArgsConstructor
@Entity
@Table(name = "page", indexes = @Index(name = "path_index", columnList = "path", unique = true))
public class Page implements Serializable {
    @Id
    @Column(name = "id", columnDefinition = "INT")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @NotNull
    private int id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    @NotNull
    private Site site;

    @Column(name = "path", columnDefinition = "TEXT")
    @NotNull
    private String path;

    @Column(name = "code", columnDefinition = "INT")
    @NotNull
    private int code;

    @Column(name = "content", columnDefinition = "MEDIUMTEXT")
    @NotNull
    private String content;

    public Page(@NotNull int code, @NotNull String content, @NotNull String path, @NotNull Site site) {
        this.code = code;
        this.content = content;
        this.path = path;
        this.site = site;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Site getSite() {
        return site;
    }

    public void setSite(Site site) {
        this.site = site;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
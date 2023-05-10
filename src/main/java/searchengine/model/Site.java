package searchengine.model;

import lombok.NoArgsConstructor;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.LocalDateTime;

@NoArgsConstructor
@Entity
@Table(name = "site")
public class Site implements Serializable {
    @Id
    @Column(name = "id", columnDefinition = "INT")
    @NotNull
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "status", columnDefinition = "ENUM ('INDEXING', 'INDEXED', 'FAILED')")
    @Enumerated(EnumType.STRING)
    @NotNull
    private Status status;

    @Column(name = "status_time", columnDefinition = "DATETIME")
    @NotNull
    private LocalDateTime statusTime;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "url", columnDefinition = "VARCHAR(255)")
    @NotNull
    private String url;

    @Column(name = "name", columnDefinition = "VARCHAR(255)")
    @NotNull
    private String name;

    public Site(String lastError, @NotNull String name, @NotNull Status status, @NotNull LocalDateTime statusTime, @NotNull String url) {
        this.lastError = lastError;
        this.name = name;
        this.status = status;
        this.statusTime = statusTime;
        this.url = url;
    }

    public Site(String lastError, @NotNull String name, @NotNull LocalDateTime statusTime, @NotNull String url) {
        this.lastError = lastError;
        this.name = name;
        this.statusTime = statusTime;
        this.url = url;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public LocalDateTime getStatusTime() {
        return statusTime;
    }

    public void setStatusTime(LocalDateTime statusTime) {
        this.statusTime = statusTime;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
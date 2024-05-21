package rent.history.checker.entity;


import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "issues")
public class Issue {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "flat_id", nullable = false)
    private Flat flat;

    @Lob
    private String description;

    private LocalDate reportedDate;
    private LocalDate resolvedDate;

    private boolean isResolved;

    public void setId(Long id) {
        this.id = id;
    }

    public void setFlat(Flat flat) {
        this.flat = flat;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setReportedDate(LocalDate reportedDate) {
        this.reportedDate = reportedDate;
    }

    public void setResolvedDate(LocalDate resolvedDate) {
        this.resolvedDate = resolvedDate;
    }

    public void setResolved(boolean resolved) {
        isResolved = resolved;
    }

    public Long getId() {
        return id;
    }

    public Flat getFlat() {
        return flat;
    }

    public String getDescription() {
        return description;
    }

    public LocalDate getReportedDate() {
        return reportedDate;
    }

    public LocalDate getResolvedDate() {
        return resolvedDate;
    }

    public boolean isResolved() {
        return isResolved;
    }
}

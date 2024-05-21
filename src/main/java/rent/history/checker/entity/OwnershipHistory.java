package rent.history.checker.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "ownership_histories")
public class OwnershipHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "flat_id", nullable = false)
    private Flat flat;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    private LocalDate ownershipStartDate;
    private LocalDate ownershipEndDate;

    public void setId(Long id) {
        this.id = id;
    }

    public void setFlat(Flat flat) {
        this.flat = flat;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public void setOwnershipStartDate(LocalDate ownershipStartDate) {
        this.ownershipStartDate = ownershipStartDate;
    }

    public void setOwnershipEndDate(LocalDate ownershipEndDate) {
        this.ownershipEndDate = ownershipEndDate;
    }

    public Long getId() {
        return id;
    }

    public Flat getFlat() {
        return flat;
    }

    public User getOwner() {
        return owner;
    }

    public LocalDate getOwnershipStartDate() {
        return ownershipStartDate;
    }

    public LocalDate getOwnershipEndDate() {
        return ownershipEndDate;
    }
}

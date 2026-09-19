package rent.history.checker.entity;


import jakarta.annotation.Nonnull;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

@Entity
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @Column(unique = true)
    private String username;

    @NotBlank
    @Column(unique = true)
    private String telephone;


    private String password;

    @NotBlank
    private String name;

    private String address;

    @Email
    @NotBlank
    @Column(unique = true)
    private String email;

    private LocalDate dateOfBirth;

    private String role;
}
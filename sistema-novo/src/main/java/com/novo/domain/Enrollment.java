package com.novo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "enrollment")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Enrollment {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "legacy_id", unique = true)
    private Long legacyId;

    @Column(name = "student_id", nullable = false)
    private String studentId;

    @Column(name = "academic_term", nullable = false)
    private String academicTerm;

    @Column(name = "status", nullable = false)
    private String status; // PENDING, CONFIRMED, CANCELLED

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}

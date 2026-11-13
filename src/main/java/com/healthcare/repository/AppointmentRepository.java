package com.healthcare.repository;

import com.healthcare.model.Appointment;
import com.healthcare.model.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    List<Appointment> findByDoctorIdAndDate(Long doctorId, LocalDate date);

    long countByDoctor_IdAndDateAndStatusNotIn(
            Long doctorId, LocalDate date, Collection<AppointmentStatus> excludedStatuses);

    @Query("""
            select coalesce(max(appointment.tokenNumber), 0)
            from Appointment appointment
            where appointment.department.id = :departmentId and appointment.date = :date
            """)
    int findMaximumTokenNumber(@Param("departmentId") Long departmentId, @Param("date") LocalDate date);

    @Query("""
            select appointment from Appointment appointment
            where (:date is null or appointment.date = :date)
              and (:departmentId is null or appointment.department.id = :departmentId)
              and (:status is null or appointment.status = :status)
            order by appointment.date, appointment.tokenNumber
            """)
    List<Appointment> search(
            @Param("date") LocalDate date,
            @Param("departmentId") Long departmentId,
            @Param("status") AppointmentStatus status);

    List<Appointment> findByDateBetweenOrderByDateAscTokenNumberAsc(LocalDate start, LocalDate end);

    boolean existsByDepartment_Id(Long departmentId);
}

package omid.springframework.msscssm.repository;

import omid.springframework.msscssm.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment,Long> {
}
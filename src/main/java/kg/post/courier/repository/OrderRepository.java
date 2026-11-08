package kg.post.courier.repository;

import kg.post.courier.entity.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, String> {
    List<OrderEntity> findAllByOrderByCreatedAtDesc();
    List<OrderEntity> findByCourierIdOrderByRouteSeqAsc(String courierId);
    boolean existsByCarPlateOrRecipientPhone(String carPlate, String recipientPhone);
}


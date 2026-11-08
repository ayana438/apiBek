package kg.post.courier.repository;

import kg.post.courier.entity.TransferEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransferRepository extends JpaRepository<TransferEntity, String> {
    List<TransferEntity> findAllByOrderByCreatedUtcDesc();
}


package kg.post.courier.repository;

import kg.post.courier.entity.CourierEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CourierRepository extends JpaRepository<CourierEntity, String> {
    List<CourierEntity> findAllByOrderByIdAsc();
}


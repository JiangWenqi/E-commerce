package info.jiangwenqi.e_commerce.repository;

import info.jiangwenqi.e_commerce.model.Cart;
import info.jiangwenqi.e_commerce.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @author wenqi
 */
@Repository
public interface CartRepository extends JpaRepository<Cart, Integer> {
    List<Cart> findAllByUserOrderByCreatedDateDesc(User user);

    void deleteByUser(User user);
}

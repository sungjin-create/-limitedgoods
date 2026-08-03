package com.limitedgoods.limitedgoods.product.repository;

import com.limitedgoods.limitedgoods.product.entity.Product;
import com.limitedgoods.limitedgoods.product.entity.ProductStatus;
import com.limitedgoods.limitedgoods.product.entity.ProductType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select p
        from Product p
        where p.id = :id
        """)
    Optional<Product> findByIdWithLock(Long id);

    @Query("""
    select p
    from Product p 
    where p.status in(
       :PREPARING, :SCHEDULED, :ACTIVE, :PAUSED
    )
    """)
    Page<Product> findProductByStatusIn(
            @Param("PREPARING") ProductStatus preparing,
            @Param("SCHEDULED") ProductStatus scheduled,
            @Param("ACTIVE") ProductStatus active,
            @Param("PAUSED") ProductStatus paused,
            Pageable pageable);

    @Modifying(flushAutomatically = true)
    @Query("""
    update Product p
        set p.stock = p.stock - :quantity
    where p.id = :productId
        and :quantity > 0
        and p.stock >= :quantity
        and (
            p.status = :activeStatus and 
                (p.saleStartAt is null or p.saleStartAt <= current_timestamp)
            or (p.status = :scheduledStatus and 
                (p.saleStartAt is not null and p.saleStartAt <= current_timestamp))
       )
        and (
            p.saleEndAt is null
            or p.saleEndAt > current_timestamp
        )
    """)
    int decreaseStockIfPurchasable(
            @Param("productId") Long productId,
            @Param("quantity") int quantity,
            @Param("activeStatus") ProductStatus activeStatus,
            @Param("scheduledStatus") ProductStatus scheduledStatus);

    @Query("""
    select p.id
        from Product p
    where p.id in :productIds
        and p.stock = 0
    """)
    List<Long> findSoldOutProductIds(@Param("productIds") Collection<Long> productIds);

    @Modifying(flushAutomatically = true)
    @Query("""
    update Product p
        set p.stock = p.stock + :quantity
     where p.id = :id
    """)
    int increaseStock(@Param("id") Long id, @Param("quantity") int quantity);

    @Modifying
    @Query("""
    update Product p
        set p.soldCount = p.soldCount + :quantity
    where p.id = :productId
    """)
    void increaseSoldCount(Long productId, int quantity);

    @Query("""
    select p
    from Product p
    where p.status in :statuses
      and (
        upper(p.name) like upper(concat('%', :keyword, '%'))
        or upper(p.description) like upper(concat('%', :keyword, '%'))
      )
    """)
    Page<Product> searchByKeywordAndStatusIn(
            Pageable pageable,
            @Param("keyword") String keyword,
            @Param("statuses") Collection<ProductStatus> statuses);

    @Query("""
    select case when count(p) > 0 then true else false end
    from Product p
    where p.id = :productId
        and p.stock <= 0
    """)
    boolean isSoldOut(@Param("productId") Long productId);

    @Query("""
    select p.stock
    from Product p
    where p.id = :productId
    """)
    Optional<Integer> findStockById(@Param("productId") Long productId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
    update Product p
        set p.status = :activeStatus,
            p.updatedAt = current_timestamp
    where p.status = :scheduledStatus
        and p.saleStartAt <= current_timestamp
    """)
    int activateProductsReadyForSale(
            @Param("activeStatus") ProductStatus activeStatus,
            @Param("scheduledStatus") ProductStatus scheduledStatus
    );

    List<Product> findAllByType(ProductType type);
}

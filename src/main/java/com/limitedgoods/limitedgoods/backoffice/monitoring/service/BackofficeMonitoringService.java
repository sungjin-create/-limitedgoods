package com.limitedgoods.limitedgoods.backoffice.monitoring.service;

import com.limitedgoods.limitedgoods.backoffice.client.PrometheusClient;
import com.limitedgoods.limitedgoods.backoffice.monitoring.dto.BackofficeBusinessMetricResponse;
import com.limitedgoods.limitedgoods.order.entity.OrderStatus;
import com.limitedgoods.limitedgoods.order.repository.OrderRepository;
import com.limitedgoods.limitedgoods.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BackofficeMonitoringService {

    private final PrometheusClient prometheusClient;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final HealthEndpoint healthEndpoint;

    public Map<String, Object> getOverview() {

        Map<String, String> dependencies = new LinkedHashMap<>();

        dependencies.put(
                "application",
                getHealthStatus("liveness")
        );

        dependencies.put(
                "database",
                getHealthStatus("db")
        );

        dependencies.put(
                "redis",
                getHealthStatus("redis")
        );


        Map requestsPerSecond = prometheusClient.query("""
            sum(
              rate(
                http_server_requests_seconds_count{
                  job="limitedgoods",
                  uri!~"/actuator.*"
                }[5m]
              )
            )
            """);

        Map errorPercent = prometheusClient.query("""
            100 *
            (
              (
                sum(
                  rate(
                    http_server_requests_seconds_count{
                      job="limitedgoods",
                      uri!~"/actuator.*",
                      status=~"5.."
                    }[5m]
                  )
                )
                or vector(0)
              )
              /
              clamp_min(
                (
                  sum(
                    rate(
                      http_server_requests_seconds_count{
                        job="limitedgoods",
                        uri!~"/actuator.*"
                      }[5m]
                    )
                  )
                  or vector(0)
                ),
                0.000001
              )
            )
            """);

        Map latencyP95 = prometheusClient.query("""
            histogram_quantile(
              0.95,
              sum by (le) (
                rate(
                  http_server_requests_seconds_bucket{
                    job="limitedgoods",
                    uri!~"/actuator.*"
                  }[5m]
                )
              )
            )
            """);

        Map availability = prometheusClient.query("""
            100 *
            (
              sum(
                rate(
                  http_server_requests_seconds_count{
                    job="limitedgoods",
                    uri!~"/actuator.*",
                    status!~"5.."
                  }[5m]
                )
              )
              /
              sum(
                rate(
                  http_server_requests_seconds_count{
                    job="limitedgoods",
                    uri!~"/actuator.*"
                  }[5m]
                )
              )
            )
            """);
        Map paymentCompletedCount = prometheusClient.query("""
            round(
              sum(
                increase(
                  limitedgoods_payment_total{
                    result="success"
                  }[1h]
                )
              )
            ) or vector(0)
            """);

        Map paymentFailurePercent = prometheusClient.query("""
            100 *
            (
              (
                sum(
                  increase(
                    limitedgoods_payment_total{
                      result="failure"
                    }[1h]
                  )
                )
                or vector(0)
              )
              /
              clamp_min(
                (
                  sum(
                    increase(
                      limitedgoods_payment_total{
                        result="success"
                      }[1h]
                    )
                  )
                  or vector(0)
                )
                +
                (
                  sum(
                    increase(
                      limitedgoods_payment_total{
                        result="failure"
                      }[1h]
                    )
                  )
                  or vector(0)
                ),
                1
              )
            )
            """);

        Map stockShortageFailureCount = prometheusClient.query("""
            round(
              sum(
                increase(
                  limitedgoods_order_create_total{
                    result="failure",
                    reason=~"PRODUCT_002|QUEUE_001"
                  }[1h]
                )
              )
            ) or vector(0)
            """);

        Map expiredOrderCount = prometheusClient.query("""
            round(
              sum(
                increase(
                  limitedgoods_order_expired_total[1h]
                )
              )
            ) or vector(0)
            """);
        Map cpuUsagePercent = prometheusClient.query("""
            100 * max(
              process_cpu_usage{
                job="limitedgoods"
              }
            )
            """);

        Map heapUsagePercent = prometheusClient.query("""
            100 *
            (
              sum(
                jvm_memory_used_bytes{
                  job="limitedgoods",
                  area="heap"
                }
              )
              /
              sum(
                jvm_memory_max_bytes{
                  job="limitedgoods",
                  area="heap"
                } > 0
              )
            )
            """);

        Map gcAveragePauseMillis = prometheusClient.query("""
            1000 *
            (
              (
                sum(
                  rate(
                    jvm_gc_pause_seconds_sum{
                      job="limitedgoods"
                    }[5m]
                  )
                )
                or vector(0)
              )
              /
              clamp_min(
                (
                  sum(
                    rate(
                      jvm_gc_pause_seconds_count{
                        job="limitedgoods"
                      }[5m]
                    )
                  )
                  or vector(0)
                ),
                0.000001
              )
            )
            """);

        Map liveThreadCount = prometheusClient.query("""
            max(
              jvm_threads_live_threads{
                job="limitedgoods"
              }
            ) or vector(0)
            """);
        Map hikariUsagePercent = prometheusClient.query("""
            (
              100 *
              max(
                hikaricp_connections_active{
                  job="limitedgoods"
                }
                /
                clamp_min(
                  hikaricp_connections_max{
                    job="limitedgoods"
                  },
                  1
                )
              )
            ) or vector(0)
            """);

        Map<String, Object> overview = new LinkedHashMap<>();

        overview.put("requestsPerSecond", requestsPerSecond);
        overview.put("errorPercent", errorPercent);
        overview.put("latencyP95", latencyP95);
        overview.put("availability", availability);
        overview.put("expiredOrderCount", expiredOrderCount);
        overview.put("paymentCompletedCount", paymentCompletedCount);
        overview.put("paymentFailurePercent", paymentFailurePercent);
        overview.put("stockShortageFailureCount", stockShortageFailureCount);

        overview.put("cpuUsagePercent", cpuUsagePercent);
        overview.put("heapUsagePercent", heapUsagePercent);
        overview.put("gcAveragePauseMillis", gcAveragePauseMillis);
        overview.put("liveThreadCount", liveThreadCount);
        overview.put("hikariUsagePercent", hikariUsagePercent);
        overview.put("dependencies", dependencies);

        return overview;
    }

    public BackofficeBusinessMetricResponse getBusinessMetrics() {
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        LocalDateTime startOfTomorrow = startOfToday.plusDays(1);

        List<OrderStatus> revenueStatuses = List.of(
                OrderStatus.PAID,
                OrderStatus.COMPLETED
        );

        long todayOrderCount = orderRepository.countOrdersCreatedBetween(
                startOfToday,
                startOfTomorrow
        );

        long todayPaidOrderCount = orderRepository.countPaidOrdersBetween(
                startOfToday,
                startOfTomorrow,
                revenueStatuses
        );

        long todayRevenue = orderRepository.sumRevenueBetween(
                startOfToday,
                startOfTomorrow,
                revenueStatuses
        );

        long soldOutProductCount = productRepository.countSoldOutProducts();

        return BackofficeBusinessMetricResponse.builder()
                .todayOrderCount(todayOrderCount)
                .todayPaidOrderCount(todayPaidOrderCount)
                .todayRevenue(todayRevenue)
                .soldOutProductCount(soldOutProductCount)
                .build();
    }

    private String getHealthStatus(String component) {
        HealthComponent health =
                healthEndpoint.healthForPath(component);

        if (health == null) {
            return "UNKNOWN";
        }

        return health.getStatus().getCode();
    }
}
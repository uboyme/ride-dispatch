package com.caojie.ridedispatch.simulator;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;



/**
 * 司机模拟器：模拟一批司机和乘客，通过 HTTP 调用 ride-dispatch 的接口。
 * 先启动项目，再运行这个类的 main 方法。
 */
public class DriverSimulator {

    // ===== 可调参数 =====
    static final String BASE_URL = "http://localhost:8080";
    static final int DRIVER_COUNT = 10;             // 司机数量
    static final int RUN_SECONDS = 120;             // 运行多久
    static final long ORDER_INTERVAL_MS = 2000;     // 乘客多久下一单
    static final long REPORT_INTERVAL_MS = 3000;    // 司机多久上报一次位置
    static final double CENTER_LNG = 116.400, CENTER_LAT = 39.910;
    static final double ACCEPT_RATE = 0.7;          // 70% 接单
    static final double REJECT_RATE = 0.15;         // 15% 拒单，剩下 15% 不理，等着超时

    static final HttpClient http = HttpClient.newHttpClient();
    static final JsonMapper json = JsonMapper.builder().build();
    static final long PHONE_BASE = System.currentTimeMillis() / 1000 % 1_000_000;
    static volatile boolean running = true;

    // ===== 统计 =====
    static final AtomicInteger ordersCreated = new AtomicInteger();
    static final AtomicInteger accepted = new AtomicInteger();
    static final AtomicInteger acceptFailed = new AtomicInteger();
    static final AtomicInteger rejected = new AtomicInteger();
    static final AtomicInteger ignored = new AtomicInteger();
    static final AtomicInteger completed = new AtomicInteger();
    static final AtomicInteger reOnline = new AtomicInteger();
    static final AtomicInteger errors = new AtomicInteger();


    public static void main(String[] args) throws Exception{
        try(var executor = Executors.newVirtualThreadPerTaskExecutor()){
            for (int i = 1; i <= DRIVER_COUNT; i++) {
                int no = i;
                executor.submit(()->runDriver(no));
            }
            executor.submit(DriverSimulator::runPassengers);
            executor.submit(DriverSimulator::printStatsLoop);

            Thread.sleep(RUN_SECONDS * 1000L);
            running = false;
        }
        System.out.println("===== 模拟结束 =====");
        printStats();
    }

    private static void runDriver(int no) {
        try {
            long driverId = createDriver(no);
            double lng = CENTER_LNG + random(-0.02, 0.02);
            double lat = CENTER_LAT + random(-0.02, 0.02);
            post("/drivers/" + driverId + "/online", location(lng, lat));

            long ignoredOrderId = -1;   // 决定不理的那一单，避免下一轮又重新决定
            int loop = 0;
            while (running) {
                // 1. 稍微移动一下，上报位置
                lng += random(-0.0005, 0.0005);
                lat += random(-0.0005, 0.0005);
                post("/drivers/" + driverId + "/location", location(lng, lat));

                // 2. 看看有没有派给我的新单
                JsonNode order = get("/drivers/" + driverId + "/current-order").path("data");
                if (order.isObject() && order.path("status").asInt() == 20) {
                    long orderId = order.path("id").asLong();
                    if (orderId != ignoredOrderId) {
                        double r = ThreadLocalRandom.current().nextDouble();
                        if (r < ACCEPT_RATE) {
                            runTrip(driverId, orderId);
                        } else if (r < ACCEPT_RATE + REJECT_RATE) {
                            post("/orders/" + orderId + "/reject?driverId=" + driverId, null);
                            rejected.incrementAndGet();
                        } else {
                            ignoredOrderId = orderId;
                            ignored.incrementAndGet();
                        }
                    }
                }

                // 3. 每 5 轮看一次自己是不是被系统设为离线了，是的话重新上线
                if (++loop % 5 == 0) {
                    int status = get("/drivers/" + driverId).path("data").path("status").asInt();
                    if (status == 0) {
                        post("/drivers/" + driverId + "/online", location(lng, lat));
                        reOnline.incrementAndGet();
                    }
                }
                Thread.sleep(REPORT_INTERVAL_MS);
            }
        } catch (Exception e) {
            errors.incrementAndGet();
            System.out.println("司机 " + no + " 出错：" + e.getMessage());
        }
    }

    // 跑完一趟行程：接单 → 出发 → 到达 → 上车 → 完成
    static void runTrip(long driverId, long orderId) throws Exception {
        String q = "?driverId=" + driverId;
        JsonNode r = post("/orders/" + orderId + "/accept" + q, null);
        if (r.path("code").asInt() != 0) {   // 可能刚好超时被改派了
            acceptFailed.incrementAndGet();
            return;
        }
        accepted.incrementAndGet();
        post("/orders/" + orderId + "/depart" + q, null);
        Thread.sleep(2000);   // 开车去接乘客
        post("/orders/" + orderId + "/arrive" + q, null);
        Thread.sleep(1000);   // 等乘客上车
        post("/orders/" + orderId + "/pickup" + q, null);
        Thread.sleep(5000);   // 行程中
        post("/orders/" + orderId + "/complete" + q, null);
        completed.incrementAndGet();
    }

    static long createDriver(int no) throws Exception {
        String phone = String.format("13%09d", PHONE_BASE * 1000 + no);
        String body = String.format(
                "{\"name\":\"模拟司机%d\",\"phone\":\"%s\",\"carPlate\":\"京S%05d\",\"carModel\":\"测试车\",\"carColor\":\"白色\",\"serviceType\":1}",
                no, phone, no);
        JsonNode r = post("/drivers", body);
        if (r.path("code").asInt() != 0) throw new IllegalStateException("创建司机失败：" + r);
        return r.path("data").path("id").asLong();
    }

    // 乘客：每隔一段时间在中心点附近随机下一单
    static void runPassengers() {
        while (running) {
            try {
                String body = String.format(Locale.ROOT,
                        "{\"passengerId\":%d,\"serviceType\":1,\"startLng\":%.6f,\"startLat\":%.6f,\"startAddress\":\"模拟起点\",\"endLng\":%.6f,\"endLat\":%.6f,\"endAddress\":\"模拟终点\"}",
                        ThreadLocalRandom.current().nextLong(1000, 2000),
                        CENTER_LNG + random(-0.02, 0.02), CENTER_LAT + random(-0.02, 0.02),
                        CENTER_LNG + random(-0.05, 0.05), CENTER_LAT + random(-0.05, 0.05));
                post("/orders", body);
                ordersCreated.incrementAndGet();
                Thread.sleep(ORDER_INTERVAL_MS);
            } catch (Exception e) {
                errors.incrementAndGet();
            }
        }
    }

    static void printStatsLoop() {
        while (running) {
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                return;
            }
            printStats();
        }
    }

    static void printStats() {
        System.out.printf("下单 %d｜接单 %d｜接单失败 %d｜拒单 %d｜不理 %d｜完成 %d｜重新上线 %d｜出错 %d%n",
                ordersCreated.get(), accepted.get(), acceptFailed.get(), rejected.get(),
                ignored.get(), completed.get(), reOnline.get(), errors.get());
    }

    static String location(double lng, double lat) {
        return String.format(Locale.ROOT, "{\"lng\":%.6f,\"lat\":%.6f}", lng, lat);
    }

    static double random(double min, double max) {
        return ThreadLocalRandom.current().nextDouble(min, max);
    }

    static JsonNode post(String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(BASE_URL + path));
        if (body == null) {
            builder.POST(HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
        }
        return send(builder.build());
    }

    static JsonNode get(String path) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(BASE_URL + path)).GET().build());
    }

    static JsonNode send(HttpRequest request) throws Exception {
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        return json.readTree(response.body());
    }
}

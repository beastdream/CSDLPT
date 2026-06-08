# TPC-C New Order Simulator - Cơ sở dữ liệu phân tán

## 1. Giới thiệu đề tài

Đây là đồ án mô phỏng giao dịch **New Order** trong hệ **cơ sở dữ liệu phân tán** dựa trên mô hình TPC-C thu gọn. Hệ thống được xây dựng nhằm minh họa cách một giao dịch phân tán có thể tác động đến nhiều site dữ liệu khác nhau, đồng thời xử lý các vấn đề thường gặp như lỗi giữa chừng, giao dịch trùng lặp, bù trừ dữ liệu và phục hồi trạng thái.

Project sử dụng:

- **Spring Boot** làm tầng xử lý nghiệp vụ và cung cấp REST API.
- **MySQL 8.0** làm hệ quản trị cơ sở dữ liệu.
- **Docker Compose** để khởi tạo 2 database site độc lập.
- **Python** để sinh dữ liệu mẫu và chạy benchmark đo hiệu năng.

Hệ thống mô phỏng bài toán đặt hàng trong môi trường bán lẻ. Khi người dùng tạo một đơn hàng mới, chương trình phải:

1. Kiểm tra khách hàng và khu vực ở Site 2.
2. Trừ tồn kho ở Site 1.
3. Tạo đơn hàng ở Site 2.
4. Cập nhật trạng thái giao dịch toàn cục.
5. Nếu có lỗi ở giữa quá trình, hệ thống phải có khả năng compensation hoặc recovery.

---

## 2. Mục tiêu của dự án

Project được xây dựng với các mục tiêu chính:

- Mô phỏng một hệ CSDL phân tán gồm **2 site MySQL độc lập**.
- Thiết kế dữ liệu theo hướng **phân mảnh theo chức năng**:
  - Site 1 quản lý kho hàng, sản phẩm và tồn kho.
  - Site 2 quản lý khách hàng, khu vực, đơn hàng và log giao dịch toàn cục.
- Cài đặt giao dịch phân tán mức ứng dụng cho nghiệp vụ **New Order**.
- Bổ sung cơ chế **Stock Reservation** để tránh trừ kho nhiều lần khi request bị lặp.
- Bổ sung **Global Transaction Log** để theo dõi trạng thái giao dịch.
- Hỗ trợ **idempotency** thông qua `txId`.
- Có cơ chế **compensating transaction** khi giao dịch thất bại.
- Có API **recovery thủ công** để xử lý các giao dịch bị treo hoặc chưa hoàn tất.
- Sinh dữ liệu mẫu tự động bằng Python.
- Benchmark hệ thống với nhiều mức người dùng đồng thời để đánh giá TPS và latency.

---

## 3. Công nghệ sử dụng

| Thành phần | Công nghệ |
|---|---|
| Backend | Spring Boot |
| ORM/Data access | Spring Data JPA |
| Database | MySQL 8.0 |
| Container | Docker, Docker Compose |
| Data generation | Python |
| Benchmark | Python, requests, matplotlib |
| Build tool | Maven Wrapper |
| Ngôn ngữ chính | Java, Python, SQL |

---

## 4. Kiến trúc hệ thống

```text
                         +-----------------------------+
                         |      Benchmark Client       |
                         |        load_test.py         |
                         +--------------+--------------+
                                        |
                                        | HTTP POST
                                        v
                         +-----------------------------+
                         |       Spring Boot API       |
                         |       localhost:8080        |
                         |   /api/orders/new           |
                         |   /api/orders/recovery/run  |
                         +--------------+--------------+
                                        |
                    +-------------------+-------------------+
                    |                                       |
                    v                                       v
+-------------------------------------+     +-------------------------------------+
| Site 1: Warehouse Database          |     | Site 2: Customer Database           |
| MySQL 8.0                           |     | MySQL 8.0                           |
| localhost:3308                      |     | localhost:3307                      |
| DB: warehouse_db                    |     | DB: customer_db                     |
+-------------------------------------+     +-------------------------------------+
| Tables:                             |     | Tables:                             |
| - Warehouse                         |     | - District                          |
| - Item                              |     | - Customer                          |
| - Stock                             |     | - Orders                            |
| - StockReservation                  |     | - GlobalTransactionLog              |
+-------------------------------------+     +-------------------------------------+
```

Hệ thống không dùng transaction manager phân tán như 2PC/XA. Thay vào đó, project triển khai giao dịch phân tán ở tầng ứng dụng bằng cách kết hợp:

- Local transaction riêng cho từng site.
- Global transaction log.
- Stock reservation.
- Compensation.
- Recovery.

Cách thiết kế này phù hợp để minh họa tư tưởng xử lý giao dịch phân tán trong môn CSDL phân tán.

---

## 5. Cấu trúc thư mục

```text
CSDLPT/
├── docker-compose.yml
├── generate_data.py
├── load_test.py
├── requirements.txt
├── README.md
│
├── db/
│   ├── site1/
│   │   └── 01_schema.sql
│   └── site2/
│       └── 01_schema.sql
│
├── results/
│   ├── raw_runs.csv
│   ├── summary.csv
│   ├── benchmark_report.md
│   ├── tps_line.png
│   ├── latency_line.png
│   └── tps_heatmap.png
│
└── tpcc/tpcc/
    ├── pom.xml
    ├── mvnw
    ├── mvnw.cmd
    └── src/main/
        ├── java/com/example/tpcc/
        │   ├── TpccApplication.java
        │   ├── controller/
        │   │   └── OrderController.java
        │   ├── dto/
        │   │   └── OrderRequest.java
        │   ├── service/
        │   │   └── NewOrderService.java
        │   ├── site1/
        │   │   ├── config/
        │   │   │   └── Site1Config.java
        │   │   ├── entity/
        │   │   │   ├── Stock.java
        │   │   │   ├── StockId.java
        │   │   │   └── StockReservation.java
        │   │   └── repo/
        │   │       ├── StockRepository.java
        │   │       └── StockReservationRepository.java
        │   └── site2/
        │       ├── config/
        │       │   └── Site2Config.java
        │       ├── entity/
        │       │   ├── Customer.java
        │       │   ├── District.java
        │       │   ├── Orders.java
        │       │   └── GlobalTransactionLog.java
        │       └── repo/
        │           ├── CustomerRepository.java
        │           ├── DistrictRepository.java
        │           ├── OrderRepository.java
        │           └── GlobalTransactionLogRepository.java
        └── resources/
            └── application.properties
```

---

## 6. Thiết kế dữ liệu phân tán

### 6.1. Site 1 - Warehouse Database

Database: `warehouse_db`  
Port: `3308`

Site 1 chịu trách nhiệm lưu trữ dữ liệu liên quan đến kho hàng và tồn kho.

| Bảng | Chức năng |
|---|---|
| `Warehouse` | Lưu thông tin kho hàng |
| `Item` | Lưu danh mục sản phẩm |
| `Stock` | Lưu số lượng tồn kho theo sản phẩm và kho |
| `StockReservation` | Lưu trạng thái giữ/trừ kho theo từng giao dịch |

Bảng quan trọng nhất trong quá trình tạo đơn hàng là `Stock` và `StockReservation`.

`Stock` có khóa chính kép:

```text
(s_i_id, s_w_id)
```

`StockReservation` dùng `tx_id` làm khóa chính để đảm bảo mỗi giao dịch chỉ được giữ kho một lần.

Các trạng thái chính của `StockReservation`:

| Trạng thái | Ý nghĩa |
|---|---|
| `RESERVED` | Đã trừ/giữ stock thành công nhưng giao dịch chưa hoàn tất |
| `CONFIRMED` | Đơn hàng đã tạo thành công, reservation được xác nhận |
| `COMPENSATED` | Stock đã được cộng trả lại do giao dịch thất bại |

### 6.2. Site 2 - Customer Database

Database: `customer_db`  
Port: `3307`

Site 2 chịu trách nhiệm lưu thông tin khách hàng, khu vực, đơn hàng và log giao dịch toàn cục.

| Bảng | Chức năng |
|---|---|
| `District` | Lưu thông tin khu vực/quận |
| `Customer` | Lưu thông tin khách hàng |
| `Orders` | Lưu đơn hàng đã tạo |
| `GlobalTransactionLog` | Lưu trạng thái toàn cục của giao dịch phân tán |

`GlobalTransactionLog` là bảng quan trọng để phục hồi giao dịch khi chương trình bị lỗi giữa chừng.

Các trạng thái chính của `GlobalTransactionLog`:

| Trạng thái | Ý nghĩa |
|---|---|
| `PENDING` | Giao dịch mới được tạo log, chưa trừ kho |
| `STOCK_RESERVED` | Đã giữ/trừ stock ở Site 1 |
| `ORDER_CREATED` | Đã tạo đơn hàng ở Site 2 nhưng chưa xác nhận reservation |
| `COMPLETED` | Giao dịch hoàn tất thành công |
| `COMPENSATING` | Đang xử lý bù trừ |
| `COMPENSATED` | Đã bù trừ xong |
| `FAILED` | Giao dịch thất bại và không cần recovery tiếp |

---

## 7. Luồng xử lý giao dịch New Order

Endpoint chính:

```http
POST /api/orders/new
```

Body mẫu:

```json
{
  "txId": "test-001",
  "itemId": 1,
  "warehouseId": 1,
  "districtId": 1,
  "customerId": 1,
  "orderQty": 1
}
```

Ý nghĩa các trường:

| Trường | Ý nghĩa |
|---|---|
| `txId` | Mã giao dịch toàn cục, dùng để chống request trùng lặp |
| `itemId` | Mã sản phẩm cần mua |
| `warehouseId` | Mã kho hàng |
| `districtId` | Mã khu vực/quận |
| `customerId` | Mã khách hàng |
| `orderQty` | Số lượng đặt hàng |

Quy trình xử lý:

```text
1. Client gửi request tạo đơn hàng đến Spring Boot API.
2. Hệ thống chuẩn hóa txId. Nếu client không truyền txId, hệ thống tự sinh UUID.
3. Kiểm tra input: itemId, warehouseId, districtId, customerId, orderQty phải hợp lệ.
4. Kiểm tra GlobalTransactionLog theo txId:
   - Nếu COMPLETED: trả về SUCCESS dạng duplicate ignored.
   - Nếu COMPENSATED hoặc FAILED: từ chối xử lý lại.
   - Nếu đang xử lý: trả về trạng thái hiện tại.
5. Tạo log giao dịch với trạng thái PENDING ở Site 2.
6. Kiểm tra Customer và District ở Site 2.
7. Trừ stock ở Site 1 và tạo StockReservation trạng thái RESERVED.
8. Cập nhật GlobalTransactionLog thành STOCK_RESERVED.
9. Tạo Orders ở Site 2 và cập nhật log thành ORDER_CREATED.
10. Xác nhận StockReservation thành CONFIRMED ở Site 1.
11. Cập nhật GlobalTransactionLog thành COMPLETED.
12. Trả về SUCCESS.
```

Nếu lỗi xảy ra sau khi stock đã bị trừ, hệ thống sẽ chạy compensation:

```text
1. Cập nhật log thành COMPENSATING.
2. Cộng trả stock ở Site 1.
3. Cập nhật StockReservation thành COMPENSATED.
4. Cập nhật GlobalTransactionLog thành COMPENSATED.
5. Trả về FAILED kèm thông báo đã bù trừ.
```

---

## 8. Cơ chế idempotency bằng txId

Trong hệ phân tán, client có thể gửi lại cùng một request do timeout hoặc lỗi mạng. Nếu không kiểm soát, hệ thống có thể bị:

- Trừ tồn kho nhiều lần.
- Tạo nhiều đơn hàng trùng nhau.
- Sai lệch dữ liệu giữa các site.

Project giải quyết bằng cách dùng `txId` làm mã định danh giao dịch toàn cục.

Nếu một `txId` đã hoàn tất với trạng thái `COMPLETED`, request lặp lại sẽ không tạo đơn hàng mới mà trả về:

```text
SUCCESS: duplicate request ignored, tx already completed. txId=...
```

Nếu `txId` từng thất bại hoặc đã compensation, hệ thống sẽ từ chối xử lý lại để tránh làm sai dữ liệu.

---

## 9. Cơ chế recovery

Endpoint recovery:

```http
POST /api/orders/recovery/run
```

Recovery dùng để xử lý các giao dịch có trạng thái chưa hoàn tất trong `GlobalTransactionLog`.

Các trạng thái được recovery quét:

```text
PENDING
STOCK_RESERVED
COMPENSATING
ORDER_CREATED
```

Cách xử lý:

| Trạng thái | Cách recovery xử lý |
|---|---|
| `PENDING` không có reservation | Chuyển thành `FAILED` |
| `PENDING` có reservation | Chạy compensation và chuyển thành `COMPENSATED` |
| `STOCK_RESERVED` | Chạy compensation và chuyển thành `COMPENSATED` |
| `COMPENSATING` | Tiếp tục compensation và chuyển thành `COMPENSATED` |
| `ORDER_CREATED` | Xác nhận reservation rồi chuyển thành `COMPLETED` |

Kết quả trả về dạng:

```text
Recovered transactions: 0
```

hoặc:

```text
Recovered transactions: 1
```

`Recovered transactions: 0` không phải lỗi. Nó chỉ có nghĩa là hiện không có giao dịch nào cần phục hồi.

---

## 10. Yêu cầu môi trường

Trước khi chạy project, cần cài:

- Docker Desktop
- JDK 21
- Python 3.10 trở lên
- Maven không bắt buộc vì project đã có Maven Wrapper

Kiểm tra phiên bản:

```bash
java -version
python --version
docker --version
```

Project đang cấu hình Java trong `pom.xml`:

```xml
<java.version>21</java.version>
```

Nếu máy chưa có JDK 21, backend có thể lỗi khi build.

---

## 11. Cách chạy chương trình

### Bước 1. Giải nén project

Ví dụ giải nén project vào ổ D:

```bat
D:
cd D:\CSDLPT
```

Cần đứng đúng thư mục có các file:

```text
docker-compose.yml
generate_data.py
load_test.py
requirements.txt
```

### Bước 2. Khởi động 2 database MySQL

Mở Docker Desktop trước, sau đó chạy:

```bat
docker compose down -v
docker compose up -d
```

Kiểm tra container:

```bat
docker ps
```

Nếu thành công, sẽ thấy 2 container:

```text
tpcc_site1_warehouse
tpcc_site2_customer
```

### Bước 3. Cài thư viện Python

```bat
python -m pip install -r requirements.txt
```

Nếu máy dùng Windows và lệnh `python` không nhận, dùng:

```bat
py -m pip install -r requirements.txt
```

### Bước 4. Sinh dữ liệu mẫu

```bat
python generate_data.py
```

Hoặc:

```bat
py generate_data.py
```

Sau khi chạy thành công, dữ liệu sẽ được thêm vào 2 database:

- `warehouse_db`
- `customer_db`

### Bước 5. Chạy backend Spring Boot

Mở terminal mới và vào thư mục backend:

```bat
cd D:\CSDLPT\tpcc\tpcc
```

Chạy Spring Boot:

```bat
mvnw.cmd spring-boot:run
```

Nếu dùng Linux/macOS:

```bash
./mvnw spring-boot:run
```

Khi thấy log có nội dung tương tự sau là backend đã chạy thành công:

```text
Tomcat started on port 8080
```

---

## 12. Test API tạo đơn hàng

### Cách 1: dùng curl trên CMD Windows

```bat
curl -X POST http://localhost:8080/api/orders/new ^
-H "Content-Type: application/json" ^
-d "{\"txId\":\"test-001\",\"itemId\":1,\"warehouseId\":1,\"districtId\":1,\"customerId\":1,\"orderQty\":1}"
```

Kết quả thành công:

```text
SUCCESS: distributed transaction completed. txId=test-001
```

### Cách 2: dùng PowerShell

```powershell
Invoke-RestMethod `
  -Uri "http://localhost:8080/api/orders/new" `
  -Method POST `
  -ContentType "application/json" `
  -Body '{"txId":"test-002","itemId":1,"warehouseId":1,"districtId":1,"customerId":1,"orderQty":1}'
```

Lưu ý: nên đổi `txId` sau mỗi lần test nếu muốn tạo giao dịch mới.

---

## 13. Test request trùng lặp

Gửi lại cùng `txId` đã thành công:

```bat
curl -X POST http://localhost:8080/api/orders/new ^
-H "Content-Type: application/json" ^
-d "{\"txId\":\"test-001\",\"itemId\":1,\"warehouseId\":1,\"districtId\":1,\"customerId\":1,\"orderQty\":1}"
```

Nếu giao dịch `test-001` đã hoàn tất, hệ thống sẽ trả về dạng:

```text
SUCCESS: duplicate request ignored, tx already completed. txId=test-001
```

Điều này chứng minh cơ chế idempotency hoạt động đúng: request bị gửi lại nhưng hệ thống không trừ kho và không tạo đơn hàng lần hai.

---

## 14. Test recovery

Gọi API:

```bat
curl -X POST http://localhost:8080/api/orders/recovery/run
```

Kết quả ví dụ:

```text
Recovered transactions: 0
```

hoặc:

```text
Recovered transactions: 1
```

Có thể dùng API recovery để kiểm tra cơ chế phục hồi sau khi tạo các tình huống lỗi như:

- Giao dịch dừng ở `PENDING`.
- Giao dịch đã trừ stock nhưng chưa tạo order.
- Giao dịch đã tạo order nhưng chưa xác nhận reservation.

---

## 15. Chạy benchmark

Đảm bảo backend đang chạy ở port 8080, sau đó mở terminal tại thư mục gốc project và chạy:

```bat
python load_test.py --runs 1 --requests-per-user 5
```

Lệnh trên sẽ chạy test nhẹ để kiểm tra hệ thống.

Để chạy benchmark đầy đủ hơn:

```bat
python load_test.py --runs 3 --requests-per-user 20
```

Script sẽ test từ 1 đến 20 concurrent users. Với mỗi mức user, script gửi nhiều request đến API `/api/orders/new`.

Sau khi chạy xong, kết quả được lưu trong thư mục `results/`.

---

## 16. Kết quả benchmark

Các file kết quả gồm:

| File | Ý nghĩa |
|---|---|
| `raw_runs.csv` | Kết quả chi tiết từng lần chạy |
| `summary.csv` | Kết quả tổng hợp theo số lượng user đồng thời |
| `benchmark_report.md` | Báo cáo benchmark tự sinh |
| `tps_line.png` | Biểu đồ TPS theo số user đồng thời |
| `latency_line.png` | Biểu đồ mean, median, P99 latency |
| `tps_heatmap.png` | Heatmap TPS theo từng lần chạy |

Các chỉ số chính:

| Chỉ số | Ý nghĩa |
|---|---|
| TPS | Số giao dịch thành công mỗi giây |
| Mean Latency | Thời gian phản hồi trung bình |
| Median Latency | Thời gian phản hồi trung vị |
| P99 Latency | 99% request có latency nhỏ hơn giá trị này |
| Saturation Point | Điểm mà tăng user không còn làm TPS tăng rõ rệt |

---

## 17. Liên hệ lý thuyết CSDL phân tán

Project có thể liên hệ với các nội dung lý thuyết sau:

### 17.1. Fragmentation / Partitioned Data

Dữ liệu được chia theo chức năng:

- Site 1 lưu dữ liệu kho hàng.
- Site 2 lưu dữ liệu khách hàng và đơn hàng.

Đây là dạng phân mảnh theo chức năng, giúp mỗi site chỉ quản lý một nhóm dữ liệu nhất định.

### 17.2. Distributed Transaction

Một giao dịch `New Order` cần thao tác trên cả hai site:

- Site 1: trừ tồn kho.
- Site 2: tạo đơn hàng.

Do đó đây là giao dịch phân tán vì dữ liệu liên quan nằm ở nhiều database khác nhau.

### 17.3. Global Transaction Log

`GlobalTransactionLog` đóng vai trò theo dõi trạng thái toàn cục của giao dịch. Nhờ bảng này, hệ thống biết giao dịch đang ở bước nào và có thể recovery khi xảy ra lỗi.

### 17.4. Compensating Transaction

Vì hệ thống không dùng 2PC/XA, khi Site 2 lỗi sau khi Site 1 đã trừ kho, hệ thống phải chạy transaction bù trừ để cộng lại stock.

Ví dụ:

```text
Đã trừ stock ở Site 1
Nhưng tạo order ở Site 2 thất bại
=> Cộng trả stock ở Site 1
```

### 17.5. Idempotency

`txId` giúp request bị gửi lại không gây trừ kho hoặc tạo order nhiều lần. Đây là yêu cầu quan trọng trong hệ thống phân tán vì lỗi mạng, timeout hoặc retry có thể xảy ra thường xuyên.

### 17.6. Cost Model

Có thể phân tích hiệu năng bằng mô hình chi phí:

```text
Cost = IO + CPU + Communication
```

Trong project:

- IO: đọc/ghi dữ liệu ở MySQL.
- CPU: xử lý logic Spring Boot, validate, transaction.
- Communication: gọi API HTTP và kết nối từ backend đến 2 database site.

Khi số concurrent users tăng, chi phí IO và communication tăng làm latency tăng và TPS có thể đạt điểm bão hòa.

---

## 18. Lỗi thường gặp và cách xử lý

### 18.1. Lỗi `Connection refused`

Nguyên nhân:

- Docker chưa chạy.
- MySQL container chưa khởi động xong.
- Backend chưa chạy.

Cách xử lý:

```bat
docker ps
```

Nếu chưa thấy container MySQL, chạy lại:

```bat
docker compose up -d
```

### 18.2. Lỗi port 3307 hoặc 3308 đã được sử dụng

Nguyên nhân: máy đang có MySQL hoặc container khác chiếm port.

Kiểm tra:

```bat
docker ps
```

Dừng container đang chiếm port:

```bat
docker stop <container_id>
```

### 18.3. Lỗi Java version

Nếu gặp lỗi dạng:

```text
invalid target release: 21
```

Nguyên nhân là máy chưa dùng JDK 21.

Kiểm tra:

```bat
java -version
```

Cách xử lý tốt nhất là cài JDK 21 và cấu hình lại `JAVA_HOME`.

### 18.4. Lỗi dùng lại txId

Nếu gửi lại cùng `txId`, hệ thống có thể trả về duplicate hoặc từ chối tùy trạng thái giao dịch trước đó.

Muốn tạo giao dịch mới, hãy đổi `txId`, ví dụ:

```json
{
  "txId": "test-003"
}
```

### 18.5. Recovery trả về 0

Kết quả:

```text
Recovered transactions: 0
```

không phải lỗi. Điều này chỉ có nghĩa là không có transaction nào đang cần phục hồi.

---

## 19. Các lệnh chạy nhanh

Chạy database:

```bat
docker compose down -v
docker compose up -d
```

Cài thư viện Python:

```bat
python -m pip install -r requirements.txt
```

Sinh dữ liệu:

```bat
python generate_data.py
```

Chạy backend:

```bat
cd tpcc\tpcc
mvnw.cmd spring-boot:run
```

Test tạo order:

```bat
curl -X POST http://localhost:8080/api/orders/new ^
-H "Content-Type: application/json" ^
-d "{\"txId\":\"test-001\",\"itemId\":1,\"warehouseId\":1,\"districtId\":1,\"customerId\":1,\"orderQty\":1}"
```

Test recovery:

```bat
curl -X POST http://localhost:8080/api/orders/recovery/run
```

Chạy benchmark:

```bat
python load_test.py --runs 1 --requests-per-user 5
```

---

## 20. Kết luận

Project đã mô phỏng được một hệ thống cơ sở dữ liệu phân tán ở mức ứng dụng với hai site MySQL độc lập. Giao dịch `New Order` không chỉ thực hiện thao tác đọc/ghi trên nhiều database mà còn có thêm các cơ chế quan trọng trong hệ phân tán như transaction log, reservation, idempotency, compensation và recovery.

Thông qua benchmark, project cũng cho phép đánh giá khả năng chịu tải của hệ thống bằng các chỉ số TPS, latency và saturation point. Đây là cơ sở để phân tích ảnh hưởng của IO, CPU và communication trong môi trường cơ sở dữ liệu phân tán.

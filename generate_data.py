import random
from datetime import datetime

import mysql.connector
from faker import Faker

fake = Faker()
random.seed(42)
Faker.seed(42)

DB1 = dict(host="127.0.0.1", port=3308, user="root", password="rootpassword", database="warehouse_db")
DB2 = dict(host="127.0.0.1", port=3307, user="root", password="rootpassword", database="customer_db")

NUM_WAREHOUSES = 10
NUM_ITEMS = 5000
NUM_STOCK_ITEMS_PER_WAREHOUSE = 2000
NUM_DISTRICTS = 50
NUM_CUSTOMERS = 10000
NUM_ORDERS = 15000






def main():
    conn1 = None
    conn2 = None
    cur1 = None
    cur2 = None

    try:
        conn1 = mysql.connector.connect(**DB1)
        conn2 = mysql.connector.connect(**DB2)

        cur1 = conn1.cursor()
        cur2 = conn2.cursor()

        cur1.execute("SET FOREIGN_KEY_CHECKS=0")
        cur1.execute("TRUNCATE TABLE StockReservation")
        cur1.execute("TRUNCATE TABLE Stock")
        cur1.execute("TRUNCATE TABLE Item")
        cur1.execute("TRUNCATE TABLE Warehouse")
        cur1.execute("SET FOREIGN_KEY_CHECKS=1")

        cur2.execute("SET FOREIGN_KEY_CHECKS=0")
        cur2.execute("TRUNCATE TABLE Orders")
        cur2.execute("TRUNCATE TABLE GlobalTransactionLog")
        cur2.execute("TRUNCATE TABLE Customer")
        cur2.execute("TRUNCATE TABLE District")
        cur2.execute("SET FOREIGN_KEY_CHECKS=1")

        warehouses = [
            (i, fake.company()[:50], fake.street_address()[:50])
            for i in range(1, NUM_WAREHOUSES + 1)
        ]

        cur1.executemany(
            "INSERT INTO Warehouse (w_id, w_name, w_street) VALUES (%s, %s, %s)",
            warehouses
        )

        items = [
            (i, fake.catch_phrase()[:50], round(random.uniform(5.0, 500.0), 2))
            for i in range(1, NUM_ITEMS + 1)
        ]

        cur1.executemany(
            "INSERT INTO Item (i_id, i_name, i_price) VALUES (%s, %s, %s)",
            items
        )

        stocks = [
            (item_id, warehouse_id, random.randint(200, 2000))
            for warehouse_id in range(1, NUM_WAREHOUSES + 1)
            for item_id in range(1, NUM_STOCK_ITEMS_PER_WAREHOUSE + 1)
        ]

        cur1.executemany(
            "INSERT INTO Stock (s_i_id, s_w_id, s_quantity) VALUES (%s, %s, %s)",
            stocks
        )

        districts = [
            (i, ((i - 1) % NUM_WAREHOUSES) + 1, fake.city()[:50])
            for i in range(1, NUM_DISTRICTS + 1)
        ]

        cur2.executemany(
            "INSERT INTO District (d_id, d_w_id, d_name) VALUES (%s, %s, %s)",
            districts
        )

        customers = [
            (
                i,
                ((i - 1) % NUM_DISTRICTS) + 1,
                fake.first_name()[:50],
                random.choice(["GC", "BC"])
            )
            for i in range(1, NUM_CUSTOMERS + 1)
        ]

        cur2.executemany(
            "INSERT INTO Customer (c_id, c_d_id, c_first, c_credit) VALUES (%s, %s, %s, %s)",
            customers
        )

        orders = [
            (
                i,
                f"seed-order-{i}",
                ((i - 1) % NUM_DISTRICTS) + 1,
                ((i - 1) % NUM_CUSTOMERS) + 1,
                fake.date_time_between(start_date="-1y", end_date="now")
            )
            for i in range(1, NUM_ORDERS + 1)
        ]

        cur2.executemany(
            "INSERT INTO Orders (o_id, tx_id, o_d_id, o_c_id, o_entry_d) VALUES (%s, %s, %s, %s, %s)",
            orders
        )

        conn1.commit()
        conn2.commit()

        total = NUM_WAREHOUSES + NUM_ITEMS + len(stocks) + NUM_DISTRICTS + NUM_CUSTOMERS + NUM_ORDERS
        print(f"DONE. Loaded {total:,} rows.")

    except Exception:
        if conn1:
            conn1.rollback()
        if conn2:
            conn2.rollback()
        raise

    finally:
        if cur1:
            cur1.close()
        if cur2:
            cur2.close()
        if conn1:
            conn1.close()
        if conn2:
            conn2.close()




if __name__ == "__main__":
    main()
    
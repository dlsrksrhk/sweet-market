CREATE TABLE members (
    id BIGINT PRIMARY KEY,
    nickname VARCHAR(30) NOT NULL,
    role VARCHAR(20) NOT NULL
);

CREATE TABLE products (
    id BIGINT PRIMARY KEY,
    seller_id BIGINT NOT NULL
);

CREATE TABLE orders (
    id BIGINT PRIMARY KEY,
    product_id BIGINT NOT NULL
);

INSERT INTO members (id, nickname, role) VALUES (1, '판매자', 'MEMBER');
INSERT INTO products (id, seller_id) VALUES (10, 1);
INSERT INTO orders (id, product_id) VALUES (100, 10);

CREATE TABLE IF NOT EXISTS ITEMS
(
    `id`       int AUTO_INCREMENT NOT NULL,
    `title`    varchar(100)       NOT NULL,
    `time` timestamp,
    `price`    float,
    `item_uuid`    varchar(255),
    PRIMARY KEY (`id`)
);
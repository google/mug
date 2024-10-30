CREATE TABLE IF NOT EXISTS ITEMS
(
    `id`       int AUTO_INCREMENT NOT NULL,
    `title`    varchar(100)       NOT NULL,
    `time` timestamp,
    `price`    float,
    PRIMARY KEY (`id`)
);
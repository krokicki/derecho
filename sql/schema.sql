DROP TABLE IF EXISTS `webqstat_job`;
CREATE TABLE `webqstat_job` (
  `id` int(11) not null AUTO_INCREMENT,
  `number` bigint(20) not null,
  `jat_prio` decimal(21,10) not null,
  `name` varchar(200) CHARACTER SET utf8 not null,
  `owner` varchar(200) CHARACTER SET utf8 not null,
  `assigned_node_id` int(11),
  `state` varchar(200) CHARACTER SET utf8 not null,
  `submission_time` datetime,
  `start_time` datetime,
  `tasks` varchar(255) CHARACTER SET utf8,
  `slots` int(11) not null,
  `full_name` varchar(200) CHARACTER SET utf8 not null,
  `binding` varchar(200) CHARACTER SET utf8 not null,
  `poll_date_time` datetime not null,
  `hard_request_name` varchar(200) CHARACTER SET utf8,
  `hard_request_resource_contribution` decimal(21,10),
  `hard_request` tinyint(1),
  `requested_pe_name` varchar(200) CHARACTER SET utf8,
  `requested_pe` int(11),
  `granted_pe_name` varchar(200) CHARACTER SET utf8,
  `granted_pe` int(11),
  PRIMARY KEY (`id`),
  UNIQUE KEY `number` (`number`,`tasks`,`poll_date_time`),
  KEY `ix6` (`poll_date_time`),
  KEY `ix5` (`state`),
  KEY `ix4` (`assigned_node_id`),
  KEY `ix3` (`owner`),
  KEY `ix2` (`number`),
  CONSTRAINT `fk_assigned_node_id_refs_id` FOREIGN KEY (`assigned_node_id`) REFERENCES `webqstat_node` (`id`)
);

DROP TABLE IF EXISTS `webqstat_node`;
CREATE TABLE `webqstat_node` (
  `id` int(11) not null AUTO_INCREMENT,
  `name` varchar(200) CHARACTER SET utf8 not null,
  `slots_total` int(11) not null,
  `arch` varchar(200) CHARACTER SET utf8 not null,
  `qtype` varchar(200) CHARACTER SET utf8 not null,
  `poll_date_time` datetime not null,
  PRIMARY KEY (`id`),
  KEY `ix2` (`poll_date_time`),
  KEY `ix1` (`name`)
);

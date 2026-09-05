# MySQL on RDS - the same engine as the local docker-compose, so the
# entities, the SQL and the CI test suite all mean the same thing on AWS.
# A single small instance: the tracker is one user's data, not a fleet.

resource "aws_db_subnet_group" "this" {
  name       = var.name
  subnet_ids = var.private_subnet_ids
  tags       = var.tags
}

# manage_master_user_password lets RDS generate and rotate the master
# password in Secrets Manager: it never appears in a .tf file, a tfvars
# file, plan output, or a person's clipboard. The task definitions read
# it straight from that secret.
resource "aws_db_instance" "this" {
  identifier = var.name

  engine         = "mysql"
  engine_version = "8.0"
  instance_class = var.instance_class

  allocated_storage = var.allocated_storage_gb
  storage_type      = "gp3"
  storage_encrypted = true
  kms_key_id        = var.kms_key_arn

  db_name                     = "job_tracker"
  username                    = "tracker"
  manage_master_user_password = true

  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [var.security_group_id]
  publicly_accessible    = false
  multi_az               = var.multi_az

  backup_retention_period   = var.backup_retention_days
  copy_tags_to_snapshot     = true
  deletion_protection       = var.deletion_protection
  skip_final_snapshot       = var.skip_final_snapshot
  final_snapshot_identifier = var.skip_final_snapshot ? null : "${var.name}-final"

  auto_minor_version_upgrade = true
  apply_immediately          = false

  tags = var.tags
}

# One VPC per environment, kept deliberately small:
#
#   public subnets   the load balancer and the Fargate tasks (with public IPs)
#   private subnets  the database, which nothing outside the VPC may reach
#
# There is NO NAT gateway. Tasks in private subnets would need one to pull
# images and ship logs, and a NAT gateway costs about $32/month before a
# single byte moves - more than everything else in this environment
# combined. Tasks in public subnets reach AWS services directly instead;
# their security group still admits inbound traffic only from the ALB, so
# the public IP is not an open door.

locals {
  az_count = length(var.azs)
  min_port = min(var.service_ports...)
  max_port = max(var.service_ports...)
}

resource "aws_vpc" "this" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true
  tags                 = merge(var.tags, { Name = var.name })
}

resource "aws_internet_gateway" "this" {
  vpc_id = aws_vpc.this.id
  tags   = merge(var.tags, { Name = var.name })
}

resource "aws_subnet" "public" {
  count = local.az_count

  vpc_id                  = aws_vpc.this.id
  cidr_block              = cidrsubnet(var.vpc_cidr, 8, count.index)
  availability_zone       = var.azs[count.index]
  map_public_ip_on_launch = true
  tags                    = merge(var.tags, { Name = "${var.name}-public-${count.index}", Tier = "public" })
}

resource "aws_subnet" "private" {
  count = local.az_count

  vpc_id            = aws_vpc.this.id
  cidr_block        = cidrsubnet(var.vpc_cidr, 8, 10 + count.index)
  availability_zone = var.azs[count.index]
  tags              = merge(var.tags, { Name = "${var.name}-private-${count.index}", Tier = "private" })
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.this.id
  }

  tags = merge(var.tags, { Name = "${var.name}-public" })
}

resource "aws_route_table_association" "public" {
  count = local.az_count

  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

# Private subnets keep the VPC's main route table: local routes only, no
# way out and no way in from the internet.

# --- security groups: ALB -> tasks -> database, nothing else -----------------

resource "aws_security_group" "alb" {
  name_prefix = "${var.name}-alb-"
  description = "Internet-facing ALB: HTTPS in (HTTP only to redirect)."
  vpc_id      = aws_vpc.this.id

  ingress {
    description = "HTTPS from anywhere"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "HTTP from anywhere, answered with a redirect to HTTPS"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(var.tags, { Name = "${var.name}-alb" })

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_security_group" "tasks" {
  name_prefix = "${var.name}-tasks-"
  description = "Fargate tasks: inbound only from the ALB on the container ports."
  vpc_id      = aws_vpc.this.id

  ingress {
    description     = "Container ports from the ALB"
    from_port       = local.min_port
    to_port         = local.max_port
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  egress {
    description = "ECR, CloudWatch Logs, SES, SNS, the AuditFlow endpoint"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(var.tags, { Name = "${var.name}-tasks" })

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_security_group" "database" {
  name_prefix = "${var.name}-db-"
  description = "MySQL: inbound only from the tasks."
  vpc_id      = aws_vpc.this.id

  ingress {
    description     = "MySQL from the tasks"
    from_port       = 3306
    to_port         = 3306
    protocol        = "tcp"
    security_groups = [aws_security_group.tasks.id]
  }

  tags = merge(var.tags, { Name = "${var.name}-db" })

  lifecycle {
    create_before_destroy = true
  }
}

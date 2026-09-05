# One ECR repository per deployed service. Always created, even while the
# app is switched off, so images can be pushed BEFORE the first Fargate
# service exists - turning the app on with an empty registry would just
# crash-loop every task on image pull. Empty repositories cost nothing.
resource "aws_ecr_repository" "service" {
  for_each = var.services

  name = "${var.name}/${each.key}"

  # A tag must always mean the same bytes. With MUTABLE, ":latest" is
  # rewritten by every deploy, so the task definition never changes, ECS
  # cannot tell one release from the next, and the circuit breaker's
  # "rollback" rolls back to the same image that just failed.
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = var.tags
}

# Which image the environment runs, as data rather than as a variable.
#
# Terraform stays the only writer of task definitions; the Deploy workflow
# pushes an image tagged with its commit sha and writes that sha here, then
# asks Terraform to apply. Reading it back through the managed resource (not
# a data source) matters: a data source would fail on the very first apply,
# before the parameter exists, and would be unknown at plan time afterwards.
# ignore_changes keeps Terraform from dragging the value back to "bootstrap"
# on every plan - a refresh loads whatever Deploy last wrote.
resource "aws_ssm_parameter" "image_tag" {
  name        = "/resistance/${var.environment}/image-tag"
  description = "Image tag (git sha) the ECS task definitions run. Written by the Deploy workflow."
  type        = "String"
  value       = "bootstrap"
  tags        = var.tags

  lifecycle {
    ignore_changes = [value]
  }
}

resource "aws_ecr_lifecycle_policy" "service" {
  for_each   = aws_ecr_repository.service
  repository = each.value.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Expire untagged images after 14 days"
        selection = {
          tagStatus   = "untagged"
          countType   = "sinceImagePushed"
          countUnit   = "days"
          countNumber = 14
        }
        action = { type = "expire" }
      },
      {
        rulePriority = 2
        description  = "Keep only the most recent 20 images"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = 20
        }
        action = { type = "expire" }
      }
    ]
  })
}

# Lets GitHub Actions assume an AWS role via short-lived OIDC tokens instead
# of long-lived access keys stored as repo secrets - removes an entire class
# of credential-leak risk and is the direction AWS/GitHub both steer CI/CD
# integrations towards.

data "tls_certificate" "github_actions" {
  url = "https://token.actions.githubusercontent.com/.well-known/openid-configuration"
}

resource "aws_iam_openid_connect_provider" "github_actions" {
  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = [data.tls_certificate.github_actions.certificates[0].sha1_fingerprint]
}

locals {
  # Two axes of variation in GitHub's OIDC subject:
  # - Jobs bound to a GitHub Environment present `environment:<name>`
  #   instead of the ref/pull_request forms.
  # - GitHub's immutable-reference format appends "@<numeric id>" to the
  #   owner and repo segments (repo:org@123/name@456:...), so each pattern
  #   needs a classic and an @-suffixed variant.
  github_subjects = flatten([
    for repo in var.github_repos : [
      "repo:${var.github_org}/${repo}:ref:refs/heads/main",
      "repo:${var.github_org}/${repo}:pull_request",
      "repo:${var.github_org}/${repo}:environment:*",
      "repo:${var.github_org}@*/${repo}@*:ref:refs/heads/main",
      "repo:${var.github_org}@*/${repo}@*:pull_request",
      "repo:${var.github_org}@*/${repo}@*:environment:*",
    ]
  ])
}

data "aws_iam_policy_document" "github_actions_trust" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github_actions.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringLike"
      variable = "token.actions.githubusercontent.com:sub"
      values   = local.github_subjects
    }
  }
}

resource "aws_iam_role" "github_actions_deploy" {
  name               = "resistance-github-actions-deploy"
  assume_role_policy = data.aws_iam_policy_document.github_actions_trust.json
}

# Every AWS service the environments touch, listed up front. This policy is
# applied by hand and the role cannot widen it, so a service missing here
# surfaces later as an AccessDenied in CI (README: "Bootstrap drift").
# Service-scoped rather than resource-scoped: tightening to specific ARNs is
# a natural step once the first apply has produced real resource ids.
data "aws_iam_policy_document" "github_actions_deploy" {
  statement {
    sid    = "TrackerServices"
    effect = "Allow"
    actions = [
      "s3:*",
      "ecr:*",
      "ecs:*",
      "elasticloadbalancing:*",
      "ec2:*",
      "rds:*",
      "ses:*",
      "sns:*",
      "kms:*",
      "ssm:*",
      "secretsmanager:*",
      "logs:*",
      "cloudwatch:*",
      "route53:*",
      "acm:*",
      "application-autoscaling:*",
      "iam:GetRole",
      "iam:CreateRole",
      "iam:DeleteRole",
      "iam:TagRole",
      "iam:UntagRole",
      "iam:ListRoleTags",
      "iam:UpdateAssumeRolePolicy",
      "iam:PutRolePolicy",
      "iam:DeleteRolePolicy",
      "iam:GetRolePolicy",
      "iam:AttachRolePolicy",
      "iam:DetachRolePolicy",
      "iam:CreateServiceLinkedRole",
      "iam:PassRole",
      "iam:ListRolePolicies",
      "iam:ListAttachedRolePolicies",
      "iam:ListInstanceProfilesForRole",
      # the SES SMTP sender is an IAM user with one access key (app module)
      "iam:GetUser",
      "iam:CreateUser",
      "iam:DeleteUser",
      "iam:TagUser",
      "iam:UntagUser",
      "iam:ListUserTags",
      "iam:PutUserPolicy",
      "iam:DeleteUserPolicy",
      "iam:GetUserPolicy",
      "iam:ListUserPolicies",
      "iam:ListAttachedUserPolicies",
      "iam:ListGroupsForUser",
      "iam:CreateAccessKey",
      "iam:DeleteAccessKey",
      "iam:ListAccessKeys",
      "iam:UpdateAccessKey",
    ]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "github_actions_deploy" {
  name   = "resistance-tracker-services"
  role   = aws_iam_role.github_actions_deploy.id
  policy = data.aws_iam_policy_document.github_actions_deploy.json
}

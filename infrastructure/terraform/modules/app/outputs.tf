output "cluster_name" {
  value = aws_ecs_cluster.this.name
}

output "app_url" {
  value = "https://${var.app_host}"
}

output "alb_dns_name" {
  value = aws_lb.this.dns_name
}

output "service_names" {
  value = [for s in aws_ecs_service.service : s.name]
}

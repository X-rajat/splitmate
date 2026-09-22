from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    app_name: str = "SplitMate"
    environment: str = "development"
    debug: bool = True

    database_url: str = "postgresql+psycopg://splitmate:splitmate@localhost:5432/splitmate"
    redis_url: str = "redis://localhost:6379/0"

    jwt_secret_key: str = "change-me-in-production"
    jwt_algorithm: str = "HS256"
    access_token_expire_minutes: int = 30
    refresh_token_expire_days: int = 30

    invitation_token_expire_days: int = 14
    invite_base_url: str = "https://splitmate.app/join"

    default_currency: str = "INR"
    supported_currencies: list[str] = ["INR", "USD", "EUR", "GBP", "AED"]

    rate_limit_per_minute: int = 60


@lru_cache
def get_settings() -> Settings:
    return Settings()

import logging

from fastapi import FastAPI, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from app.config import get_settings
from app.routers import analytics, auth, expenses, friends, groups, notifications, recurring, settlements

settings = get_settings()
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("splitmate")

app = FastAPI(
    title="SplitMate API",
    description="Expense-sharing backend for the SplitMate Android app.",
    version="1.0.0",
    docs_url="/docs",
    redoc_url="/redoc",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"] if settings.debug else [],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.exception_handler(HTTPException)
async def http_exception_handler(request: Request, exc: HTTPException):
    # Never leak stack traces / internals; keep responses XSS-safe (JSON, no HTML echo).
    return JSONResponse(status_code=exc.status_code, content={"detail": exc.detail})


@app.get("/health")
def health():
    return {"status": "ok", "app": settings.app_name, "environment": settings.environment}


app.include_router(auth.router)
app.include_router(auth.users_router)
app.include_router(groups.router)
app.include_router(expenses.router)
app.include_router(expenses.balances_router)
app.include_router(settlements.router)
app.include_router(friends.router)
app.include_router(notifications.router)
app.include_router(recurring.router)
app.include_router(analytics.router)

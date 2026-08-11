from fastapi import FastAPI

app = FastAPI(title="ShortsCap Backend")


@app.get("/")
def root():
    return {
        "status": "success",
        "message": "ShortsCap Backend is running",
    }

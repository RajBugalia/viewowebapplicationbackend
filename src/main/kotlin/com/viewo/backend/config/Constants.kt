package com.viewo.backend.config

/**
 * Global application constants.
 * If you need to change the server's external IP or base URL, you can do it here.
 * Note: Database and Server Port configurations should still be updated in application.properties.
 */
object AppConstants {
    // The current environment URL for the backend
    const val BACKEND_BASE_URL = "http://localhost:8765"
    // const val BACKEND_BASE_URL = "https://your-digital-ocean-droplet.com"

    // The frontend URL for CORS configurations
    const val FRONTEND_URL = "http://localhost:3000"
}

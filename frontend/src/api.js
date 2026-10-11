
import axios from "axios";

const api = axios.create({
    baseURL: "https://healthcare-hr1n.onrender.com/api"
});

// Automatically attach the login token to each API request
api.interceptors.request.use(
    (config) => {
        const token = localStorage.getItem("token");

        if (token) {
            config.headers.Authorization = `Bearer ${token}`;
        }

        return config;
    },
    (error) => {
        return Promise.reject(error);
    }
);

export default api;

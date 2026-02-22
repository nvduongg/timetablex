import axios from 'axios';

const instance = axios.create({
    baseURL: 'http://localhost:8080/api', // Trỏ về Backend
    timeout: 10000,
});

instance.interceptors.request.use((config) => {
    const token = localStorage.getItem('auth_token');
    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
});

export default instance;
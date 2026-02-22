import axios from '../utils/axiosConfig';

export const getRooms = () => axios.get('/rooms');
export const createRoom = (data) => axios.post('/rooms', data);
export const updateRoom = (id, data) => axios.put(`/rooms/${id}`, data);
export const deleteRoom = (id) => axios.delete(`/rooms/${id}`);
export const downloadTemplate = () => axios.get('/rooms/template', { responseType: 'blob' });
export const importRoom = (file) => {
    const formData = new FormData();
    formData.append('file', file);
    return axios.post('/rooms/import', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
    });
};
import axios from '../utils/axiosConfig';

// TimeSlots
export const getSlots = () => axios.get('/time-config/slots');
export const createSlot = (data) => axios.post('/time-config/slots', data);
export const updateSlot = (id, data) => axios.put(`/time-config/slots/${id}`, data);
export const deleteSlot = (id) => axios.delete(`/time-config/slots/${id}`);

// Shifts
export const getShifts = () => axios.get('/time-config/shifts');
export const createShift = (data) => axios.post('/time-config/shifts', data);
export const updateShift = (id, data) => axios.put(`/time-config/shifts/${id}`, data);
export const deleteShift = (id) => axios.delete(`/time-config/shifts/${id}`);

// Excel
export const downloadTemplate = () => axios.get('/time-config/template', { responseType: 'blob' });
export const importData = (file) => {
    const formData = new FormData();
    formData.append('file', file);
    return axios.post('/time-config/import', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
    });
};
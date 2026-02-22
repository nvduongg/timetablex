import axios from '../utils/axiosConfig';

export const getClasses = () => axios.get('/classes');
export const createClass = (data) => axios.post('/classes', data);
export const updateClass = (id, data) => axios.put(`/classes/${id}`, data);
export const deleteClass = (id) => axios.delete(`/classes/${id}`);
export const downloadTemplate = () => axios.get('/classes/template', { responseType: 'blob' });
export const importClass = (file) => {
    const formData = new FormData();
    formData.append('file', file);
    return axios.post('/classes/import', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
    });
};
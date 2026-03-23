import axios from '../utils/axiosConfig';

export const getDepartments = (facultyId) =>
    axios.get('/departments', { params: facultyId ? { facultyId } : {} });
export const createDepartment = (data) => axios.post('/departments', data);
export const updateDepartment = (id, data) => axios.put(`/departments/${id}`, data);
export const deleteDepartment = (id) => axios.delete(`/departments/${id}`);
export const downloadTemplate = () =>
    axios.get('/departments/template', { responseType: 'blob' });
export const importDepartment = (file) => {
    const formData = new FormData();
    formData.append('file', file);
    return axios.post('/departments/import', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
    });
};

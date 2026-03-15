import axios from '../utils/axiosConfig';

export const getCohorts = () => axios.get('/cohorts');
export const getActiveCohorts = () => axios.get('/cohorts/active');
export const createCohort = (data) => axios.post('/cohorts', data);
export const updateCohort = (id, data) => axios.put(`/cohorts/${id}`, data);
export const deleteCohort = (id) => axios.delete(`/cohorts/${id}`);


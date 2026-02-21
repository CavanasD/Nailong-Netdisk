import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router' // 引入 router 实例以进行跳转

const request = axios.create({
  baseURL: '/api',
  timeout: 5000
})

request.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers['token'] = token
    }
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// 响应拦截器
request.interceptors.response.use(
  response => {
    const res = response.data
    // 如果后端返回的状态码不是0（成功），则提示错误
    if (res.code !== 0) {
      ElMessage.error(res.message || '系统异常')
      return Promise.reject(new Error(res.message || 'Error'))
    }
    return res
  },
  error => {
    // 优先使用后端返回的错误信息（例如 WAF 拦截）
    if (error.response) {
      // 检查特定错误码或消息进行跳转
      const resData = error.response.data || {};
      const msg = resData.message;

      // 兼容中文和英文的报警信息，或者检查是否有 waf 数据特征
      if (error.response.status === 400 &&
          (msg === 'Nailong Waf警告！发现攻击！' ||
           msg === 'Nailong Defender Warning! Hacker detected' ||
           (resData.data && resData.data.secId && resData.data.riskName))) {

        const wafData = resData.data || {};
        router.push({
          name: 'WafAlert',
          query: {
            secId: wafData.secId,
            userId: wafData.userId,
            riskName: wafData.riskName,
            banned: wafData.banned,
            remainSeconds: wafData.remainSeconds,
            details: wafData.details
          }
        })

        // 返回一个未解决的 Promise，阻止原本的错误提示继续弹出（可选，或者让它不弹 Message）
        return Promise.reject(error)
      }

      if (error.response.data && error.response.data.message) {
        // 如果不是 Waf 跳转拦截，则显示错误信息
        if (msg !== 'Nailong Waf警告！发现攻击！' && msg !== 'Nailong Defender Warning! Hacker detected') {
             ElMessage.error(error.response.data.message)
        }
      } else {
        ElMessage.error(error.message || '网络异常')
      }
    } else {
       ElMessage.error(error.message || '网络异常')
    }
    return Promise.reject(error)
  }
)

export default request

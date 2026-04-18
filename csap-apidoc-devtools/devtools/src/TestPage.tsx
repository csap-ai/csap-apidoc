const TestPage = () => {
  return (
    <div style={{ padding: '50px', fontSize: '24px', color: 'red', background: 'yellow' }}>
      <h1>测试页面 - 如果你能看到这个，说明React正常工作</h1>
      <p>当前时间: {new Date().toLocaleString()}</p>
    </div>
  )
}

export default TestPage


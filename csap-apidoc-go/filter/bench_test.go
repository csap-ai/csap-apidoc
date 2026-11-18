package filter

import (
	"encoding/json"
	"fmt"
	"testing"
)

type benchItem struct {
	ID        int64             `json:"id"`
	Name      string            `json:"name"`
	LeagueID  int64             `json:"league_id"`
	HomeScore int               `json:"home_score"`
	AwayScore int               `json:"away_score"`
	Status    string            `json:"status"`
	Extra     map[string]string `json:"extra"`
	Tags      []string          `json:"tags"`
	UpdatedAt int64             `json:"updated_at"`
	Internal  string            `json:"internal"`
}

type benchResp struct {
	Code    string `json:"code"`
	Message string `json:"message"`
	Success bool   `json:"success"`
	Data    struct {
		Total int64       `json:"total"`
		Rows  []benchItem `json:"rows"`
	} `json:"data"`
}

func benchData() benchResp {
	var r benchResp
	r.Code, r.Message, r.Success = "0", "成功", true
	r.Data.Total = 100
	for i := 0; i < 100; i++ {
		r.Data.Rows = append(r.Data.Rows, benchItem{
			ID: int64(i), Name: fmt.Sprintf("比赛 %d", i), LeagueID: 7, HomeScore: 2, AwayScore: 1, Status: "finished",
			Extra: map[string]string{"a": "b", "c": "d"}, Tags: []string{"x", "y"}, UpdatedAt: 1700000000000, Internal: "secret",
		})
	}
	return r
}

var benchMask = Compile([]string{"code", "message", "data.total", "data.rows.id", "data.rows.name", "data.rows.leagueId",
	"data.rows.homeScore", "data.rows.awayScore", "data.rows.status", "data.rows.updatedAt"}, nil)

// BenchmarkEncode 是基准:只用 encoding/json 编码。
func BenchmarkEncode(b *testing.B) {
	v := benchData()
	b.ReportAllocs()
	for i := 0; i < b.N; i++ {
		_, _ = json.Marshal(v)
	}
}

// BenchmarkEncodePrune 是编码 + 裁剪。设计目标:裁剪带来的额外开销不超过编码耗时的 30%。
func BenchmarkEncodePrune(b *testing.B) {
	v := benchData()
	b.ReportAllocs()
	for i := 0; i < b.N; i++ {
		out, _ := json.Marshal(v)
		_, _ = Prune(out, benchMask)
	}
}

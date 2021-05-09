$(document).ready(function(){
    //注册分页组件
    //注意 el-pagination里的layout里选择的各组件要以逗号分隔，否则报错Could not read property: key
    //另外，使用el-pagination组件时，在pageSize和current-page上都要使用.sync修饰符，双向绑定，以免父组件无法监听到对应变量的变化，导致不产生响应
    var pagination_component = {
        props: ['pagination_data'],
        template: `<el-pagination
                        @size-change="pagination_data.handleSizeChange"
                        @current-change="pagination_data.handleCurrentPageChange"
                        :current-page.sync="pagination_data.currentPage"
                        :page-sizes="pagination_data.pageSizes"
                        :page-size.sync="pagination_data.pageSize"
                        background
                        layout="prev, pager, next, sizes"
                        :total="pagination_data.total"
                        >
                   </el-pagination>`
    }

    var vm = new Vue({
        el:"#vue_load_genre_movie",
        data: {
            genre: $("#genreContainer").val(),
            genre_movie_list: [],
            pagination_data: {
                isTotalShowed: false,
                isSizesShowed: false,
                isJumperShowed: false,
                total: 10,
                currentPage: 1,
                pageSize: 28,
                pageSizes: [28, 42],
                handleSizeChange: val => {
                    //调整页面显示条目数后回到首页
                    this.currentPage = 1;
                    this.pageSize = val;
                },
                handleCurrentPageChange: val => {
                    this.currentPage = val;
                }
            },
        },
        computed: {
            //page_size变量名与pagination_data中的pageSize不同，是为了能够正确响应
            page_size: function (){
                return this.pagination_data.pageSize;
            },
            curPageIndex: function() {
                return this.pagination_data.currentPage;
            },
            curShowingList: function() {
                return this.genre_movie_list.slice((this.curPageIndex - 1) * this.page_size, this.curPageIndex * this.page_size);
            }
        },
        components: {
            'pagination' : pagination_component
        },
        mounted: function () {
            this.getGenreMovieList();
        },
        methods: {
            getGenreMovieList: function () {
                this.$axios({
                    method: 'post',
                    url: '/collection/' + this.genre + '/getGenreMovieList',
                    data: {
                        size: 30
                    }
                })
                    .then(r => {
                        //注意与$.ajax不同的情况，回调中参数r是一个包含axios.config在内的各种参数的对象，使用r.data取到真正的回调json数据
                        //console.log(r);
                        //console.log(r.data)//这里的r.data是由PageInfo对象转换得到的JSON object，其中的list才是真正的电影列表
                        //console.log(this.curShowingList);
                        //console.log(this);
                        //注意，使用箭头函数是为了让this指向Vue实例，而不是each.item
                        $.each(r.data.list, (index, movie) => {
                            //console.log(this);
                            this.genre_movie_list.push(movie);
                        })
                        this.pagination_data.total = this.genre_movie_list.length;
                    })
                    .catch(error => {
                        console.log(error);
                    });
            }
        },
        filters: {
            ratingFormat: function(value) {
                //评价分数保留两位小数，乘100后进行上舍入再除以100
                return Math.ceil(value * 100) / 100;
            }
        }
    })

})

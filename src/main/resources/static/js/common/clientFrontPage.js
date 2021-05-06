// Navigation Scripts to Show Header on Scroll-Up
jQuery(document).ready(function($) {
    var MQL = 1170;

    //primary navigation slide-in effect
    if ($(window).width() > MQL) {
        var headerHeight = $('.navbar-custom').height(),
            bannerHeight  = $('.intro-header .container').height();
        $(window).on('scroll', {
                previousTop: 0
            },
            function() {
                var currentTop = $(window).scrollTop(),
                    $catalog = $('.side-catalog');

                //check if user is scrolling up
                if (currentTop < this.previousTop) {
                    //if scrolling up...
                    if (currentTop > 0 && $('.navbar-custom').hasClass('is-fixed')) {
                        $('.navbar-custom').addClass('is-visible');
                    } else {
                        $('.navbar-custom').removeClass('is-visible is-fixed');
                    }
                } else {
                    //if scrolling down...
                    $('.navbar-custom').removeClass('is-visible');
                    if (currentTop > headerHeight && !$('.navbar-custom').hasClass('is-fixed')) $('.navbar-custom').addClass('is-fixed');
                }
                this.previousTop = currentTop;

                //adjust the appearance of side-catalog
                $catalog.show();
                if (currentTop > (bannerHeight + 41)){
                    $catalog.addClass('fixed')
                } else {
                    $catalog.removeClass('fixed')
                }
            });
    }
});

//将axios.js中的axios挂载到Vue的原生对象中
Vue.prototype.$axios = axios;

//定义watchlist-ribbon组件，在每个电影卡片上复用
//使用时在Vue实例的components里局部注册该组件
//使用该组件时，要为传入的movie绑定响应属性isInWatchList和watchListLoading，在父组件中实现即可
var watchListRibbon = {
    props: {
        movie: Object
    },
    data: function () {
        return {

        }
    },
    methods: {
        handleAddWatchList: function(event){
            this.movie.watchListLoading = true;
            this.$axios({
                method: 'post',
                url: '/watchlist/addWatchList',
                data: {
                    movieId: this.movie.movieId
                }
            })
                .then(r => {
                    if(r.data['status'] === 'success')
                        this.movie.isInWatchList = true;
                    else
                        console.log(r.data['error_msg']);
                    this.movie.watchListLoading = false;
                })
                .catch(error => {
                    console.log(error);
                })
        },
        handleDelWatchList: function (event) {
            this.movie.watchListLoading = true;
            this.$axios({
                method: 'post',
                url: '/watchlist/delWatchList',
                data: {
                    movieId: this.movie.movieId
                }
            })
                .then(r => {
                    if(r.data['status'] === 'success')
                        this.movie.isInWatchList = false;
                    else
                        console.log(r.data['error_msg']);
                    this.movie.watchListLoading = false;
                })
                .catch(error => {
                    console.log(error);
                })
        },
    },
    template: `
        <div class="watchlist-ribbon watchlist-ribbon__inWatchList" v-if="movie.isInWatchList" @click="movie.watchListLoading===false && handleDelWatchList($event)">
            <svg class="watchlist-ribbon__bg" width="24px" height="34px" viewBox="0 0 24 34" xmlns="http://www.w3.org/2000/svg" role="presentation">
                <polygon class="watchlist-ribbon__bg-ribbon" fill="#000000" points="24 0 0 0 0 32 12.2436611 26.2926049 24 31.7728343"></polygon>
                <polygon class="watchlist-ribbon__bg-hover" points="24 0 0 0 0 32 12.2436611 26.2926049 24 31.7728343"></polygon>
                <polygon class="watchlist-ribbon__bg-shadow" points="24 31.7728343 24 33.7728343 12.2436611 28.2926049 0 34 0 32 12.2436611 26.2926049"></polygon>
            </svg>
            <div class="watchlist-ribbon__icon" role="presentation">
                <svg v-if="movie.watchListLoading === false" xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="currentColor" role="presentation">
                    <path fill="none" d="M0 0h24v24H0V0z"></path>
                    <path d="M9 16.2l-3.5-3.5a.984.984 0 0 0-1.4 0 .984.984 0 0 0 0 1.4l4.19 4.19c.39.39 1.02.39 1.41 0L20.3 7.7a.984.984 0 0 0 0-1.4.984.984 0 0 0-1.4 0L9 16.2z"></path>
                </svg>
                <svg v-else xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="24px" height="24px" viewBox="0 0 50 50" style="enable-background:new 0 0 50 50" xml:space="preserve">
                    <path fill="#000" d="M25.251,6.461c-10.318,0-18.683,8.365-18.683,18.683h4.068c0-8.071,6.543-14.615,14.615-14.615V6.461z" transform="rotate(275.098 25 25)">
                        <animateTransform attributeType="xml" attributeName="transform" type="rotate" from="0 25 25" to="360 25 25" dur="0.6s" repeatCount="indefinite"></animateTransform>
                    </path>
                </svg>
            </div>
        </div>
        <div class="watchlist-ribbon" v-else @click="movie.watchListLoading===false && handleAddWatchList($event)">
            <svg class="watchlist-ribbon__bg" width="24px" height="34px" viewBox="0 0 24 34" xmlns="http://www.w3.org/2000/svg" role="presentation">
                <polygon class="watchlist-ribbon__bg-ribbon" fill="#000000" points="24 0 0 0 0 32 12.2436611 26.2926049 24 31.7728343"></polygon>
                <polygon class="watchlist-ribbon__bg-hover" points="24 0 0 0 0 32 12.2436611 26.2926049 24 31.7728343"></polygon>
                <polygon class="watchlist-ribbon__bg-shadow" points="24 31.7728343 24 33.7728343 12.2436611 28.2926049 0 34 0 32 12.2436611 26.2926049"></polygon>
            </svg>
            <div class="watchlist-ribbon__icon" role="presentation">
                <svg v-if="movie.watchListLoading === false" xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="currentColor" role="presentation">
                    <path fill="none" d="M0 0h24v24H0V0z"></path>
                    <path d="M18 13h-5v5c0 .55-.45 1-1 1s-1-.45-1-1v-5H6c-.55 0-1-.45-1-1s.45-1 1-1h5V6c0-.55.45-1 1-1s1 .45 1 1v5h5c.55 0 1 .45 1 1s-.45 1-1 1z"></path>
                </svg>
                <svg v-else xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="24px" height="24px" viewBox="0 0 50 50" style="enable-background:new 0 0 50 50" xml:space="preserve">
                    <path fill="#f5c518" d="M25.251,6.461c-10.318,0-18.683,8.365-18.683,18.683h4.068c0-8.071,6.543-14.615,14.615-14.615V6.461z" transform="rotate(275.098 25 25)">
                        <animateTransform attributeType="xml" attributeName="transform" type="rotate" from="0 25 25" to="360 25 25" dur="0.6s" repeatCount="indefinite"></animateTransform>
                    </path>
                </svg>
            </div>
        </div>
    `
}